package com.notnoop.apns.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryError;
import com.notnoop.apns.DeliveryResult;
import com.notnoop.apns.EnhancedApnsNotification;

public class MockApnsServer {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(MockApnsServer.class);

    private final int port;
    private final AtomicInteger currentNotificationList = new AtomicInteger(-1);
    private final List<List<ApnsNotification>> receivedNotifications = new CopyOnWriteArrayList<>();
    private final Vector<CountDownLatch> countdownLatches;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    private SSLServerSocket serverSocket;
    private SSLContext sslContext;
    private Map<Integer, DeliveryResult> fails = new HashMap<Integer, DeliveryResult>();

    public static final int MAX_PAYLOAD_SIZE = 256;

    public MockApnsServer(final int port, final SSLContext sslContext) {
        this.port = port;
        this.sslContext = sslContext;
        this.countdownLatches = new Vector<CountDownLatch>();
        setupNextNotificationsList();
    }

    public void start() throws InterruptedException {
        executor.execute(new ServerRunner(port, this));
    }

    private class ServerRunner implements Runnable {
        private final int port;
        private final MockApnsServer server;

        private ServerRunner(int port, MockApnsServer server) {
            this.port = port;
            this.server = server;
        }

        @Override
        public void run() {
            try {
                serverSocket = (SSLServerSocket) sslContext
                        .getServerSocketFactory().createServerSocket(port);
                LOGGER.info("Server socket bound to port " + port);

                for (;;) {
                    // Listen for connections
                    final SSLSocket socket = (SSLSocket) serverSocket.accept();
                    // handle connection
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            new SocketHandler(server).handle(socket);
                        }
                    });
                }
            } catch (IOException e) {
                LOGGER.error("Error bounding server socket to port " + port);
                throw new RuntimeException(e);
            }
        }
    }

    public void shutdown() throws InterruptedException {
        this.executor.shutdownNow();
        if (this.serverSocket != null) {
            try {
                this.serverSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * This makes the server send a failure immediately after receiving a
     * notification with ID failWhenReceive
     * 
     * @param errorCode
     * @param idToFail
     */
    public synchronized void fail(final DeliveryError errorCode,
            final int idToFail, final int failWhenReceive) {
        fails.put(failWhenReceive, new DeliveryResult(errorCode, idToFail));
    }

    protected DeliveryResult handleReceivedNotification(
            final ApnsNotification receivedNotification) {
        synchronized (this) {
            boolean resent = false;
            for (List<Integer> ids : getReceivedNotificationIds()) {
                if (ids.contains(receivedNotification.getIdentifier())) {
                    resent = true;
                    break;
                }
            }

            addReceivedNotification(receivedNotification);

            final DeliveryResult result;
            if (fails.containsKey(receivedNotification.getIdentifier())) {
                result = fails.remove(receivedNotification.getIdentifier());
                LOGGER.debug("Causing failure...");
            } else
                result = null;

            if (!resent) {
                LOGGER.trace("Notification causing countdown "
                        + receivedNotification);
                for (final CountDownLatch latch : this.countdownLatches) {
                    latch.countDown();
                }
            }

            return result;
        }
    }

    private synchronized void setupNextNotificationsList() {
        receivedNotifications.add(new CopyOnWriteArrayList<ApnsNotification>());
        int n = currentNotificationList.incrementAndGet();
        assert (n == receivedNotifications.size() - 1);
    }

    private synchronized void addReceivedNotification(
            ApnsNotification receivedNotification) {
        receivedNotifications.get(currentNotificationList.get()).add(
                receivedNotification);
    }

    public synchronized List<List<ApnsNotification>> getReceivedNotifications() {
        return new ArrayList<>(this.receivedNotifications);
    }

    public synchronized List<List<Integer>> getReceivedNotificationIds() {
        List<List<Integer>> result = new ArrayList<>();
        for (List<ApnsNotification> connection : receivedNotifications) {
            List<Integer> ids = new ArrayList<>();
            for (ApnsNotification n : connection) {
                ids.add(n.getIdentifier());
            }
            result.add(ids);
        }
        return result;
    }

    public CountDownLatch getCountDownLatch(final int notificationCount) {
        final CountDownLatch latch = new CountDownLatch(notificationCount);
        this.countdownLatches.add(latch);

        return latch;
    }

    private static class SocketHandler {

        private boolean rejectFutureMessages = false;
        private final MockApnsServer server;

        public SocketHandler(MockApnsServer server) {
            this.server = server;
        }

        public void handle(Socket socket) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();

                // Read from input
                while (true) {
                    // Read command
                    int command = in.read();
                    if (command == -1) {
                        // EOF
                        LOGGER.trace("EOF received while reading command");
                        break;
                    } else if (command != 1) {
                        reportErrorAndCloseConnection(out, new byte[] { 0 },
                                DeliveryError.UNKNOWN);
                        break;
                    }

                    // Read identifier
                    byte[] identifier = new byte[4];
                    if (in.read(identifier) != 4) {
                        LOGGER.trace("EOF received while reading identifier");
                        break;
                    }

                    // Read expiry
                    byte[] expiry = new byte[4];
                    if (in.read(expiry) != 4) {
                        LOGGER.trace("EOF received while reading expiry");
                        break;
                    }

                    final long timestamp = (ByteBuffer.wrap(expiry).getInt() & 0xFFFFFFFFL) * 1000L;
                    Date expiration = new Date(timestamp);

                    // Read token length
                    byte[] tokenLength = new byte[2];
                    if (in.read(tokenLength) != 2) {
                        LOGGER.trace("EOF received while reading token length");
                        break;
                    }

                    // Validate token length
                    int tokenLenghtInt = ByteBuffer.wrap(tokenLength)
                            .getShort();
                    if (tokenLenghtInt == 0) {
                        this.reportErrorAndCloseConnection(out, identifier,
                                DeliveryError.MISSING_DEVICE_TOKEN);
                        break;
                    }

                    // Read token
                    byte[] token = new byte[tokenLenghtInt];
                    if (in.read(token) != tokenLenghtInt) {
                        LOGGER.trace("EOF received while reading token");
                        break;
                    }

                    // Read payload length
                    byte[] payloadLenth = new byte[2];
                    if (in.read(payloadLenth) != 2) {
                        LOGGER.trace("EOF received while reading payload length");
                        break;
                    }

                    int payloadLengthInt = ByteBuffer.wrap(payloadLenth)
                            .getShort();

                    if (payloadLengthInt > MAX_PAYLOAD_SIZE
                            || payloadLengthInt == 0) {
                        this.reportErrorAndCloseConnection(out, identifier,
                                DeliveryError.INVALID_PAYLOAD_SIZE);
                    }

                    // Read payload
                    byte[] payload = new byte[payloadLengthInt];
                    if (in.read(payload) != payloadLengthInt) {
                        LOGGER.trace("EOF received while reading payload");
                        break;
                    }

                    ApnsNotification notification = new EnhancedApnsNotification(
                            ByteBuffer.wrap(identifier).getInt(),
                            (int) (expiration.getTime() / 1000), token, payload);

                    if (!handleNotification(out, notification))
                        break;
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                try {
                    LOGGER.trace("Closing socket...");
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        private boolean handleNotification(OutputStream out,
                ApnsNotification receivedNotification) throws IOException {
            LOGGER.trace("RECEIVED " + receivedNotification);
            final DeliveryResult rejection;

            synchronized (this) {
                if (!this.rejectFutureMessages) {
                    rejection = this.server
                            .handleReceivedNotification(receivedNotification);
                    LOGGER.trace("Notification handled " + receivedNotification);

                    if (rejection != null) {
                        this.rejectFutureMessages = true;
                    }
                } else {
                    LOGGER.trace("Notification rejected "
                            + receivedNotification);
                    return true;
                }
            }

            if (rejection != null) {
                reportErrorAndCloseConnection(out, ByteBuffer.allocate(4)
                        .putInt(rejection.getId()).array(),
                        rejection.getError());
                server.setupNextNotificationsList();
                return false;
            }

            return true;
        }

        private void reportErrorAndCloseConnection(OutputStream outputStream,
                final byte[] notificationId, final DeliveryError errorCode)
                throws IOException {
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            response.write(8);
            response.write(errorCode.code());
            response.write(notificationId);

            outputStream.write(response.toByteArray());
            outputStream.close();
        }
    }

}