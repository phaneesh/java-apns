package com.notnoop.apns.internal.netty.util;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryError;
import com.notnoop.apns.DeliveryResult;
import com.notnoop.apns.EnhancedApnsNotification;

public class MockApnsServer {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private final int port;
    private final AtomicInteger currentNotificationList = new AtomicInteger(-1);
    private final List<List<ApnsNotification>> receivedNotifications = new CopyOnWriteArrayList<>();
    private final Vector<CountDownLatch> countdownLatches;

    private Integer idToFail = null;
    private Integer failWhenReceive = null;
    private DeliveryError errorCode;
    private SSLContext sslContext;

    public static final int MAX_PAYLOAD_SIZE = 256;

    private enum ApnsPushNotificationDecoderState {
        OPCODE, SEQUENCE_NUMBER, EXPIRATION, TOKEN_LENGTH, TOKEN, PAYLOAD_LENGTH, PAYLOAD
    }

    private class ApnsPushNotificationDecoder extends
            ReplayingDecoder<ApnsPushNotificationDecoderState> {

        private int sequenceNumber;
        private Date expiration;
        private byte[] token;
        private byte[] payloadBytes;

        private static final byte EXPECTED_OPCODE = 1;

        public ApnsPushNotificationDecoder() {
            super(ApnsPushNotificationDecoderState.OPCODE);
        }

        @Override
        protected void decode(final ChannelHandlerContext context,
                final ByteBuf in, final List<Object> out) {
            switch (this.state()) {
            case OPCODE: {
                final byte opcode = in.readByte();

                if (opcode != EXPECTED_OPCODE) {
                    reportErrorAndCloseConnection(context, 0,
                            DeliveryError.UNKNOWN);
                } else {
                    this.checkpoint(ApnsPushNotificationDecoderState.SEQUENCE_NUMBER);
                }

                break;
            }

            case SEQUENCE_NUMBER: {
                this.sequenceNumber = in.readInt();
                this.checkpoint(ApnsPushNotificationDecoderState.EXPIRATION);

                break;
            }

            case EXPIRATION: {
                final long timestamp = (in.readInt() & 0xFFFFFFFFL) * 1000L;
                this.expiration = new Date(timestamp);

                this.checkpoint(ApnsPushNotificationDecoderState.TOKEN_LENGTH);

                break;
            }

            case TOKEN_LENGTH: {

                this.token = new byte[in.readShort() & 0x0000FFFF];

                if (this.token.length == 0) {
                    this.reportErrorAndCloseConnection(context,
                            this.sequenceNumber,
                            DeliveryError.MISSING_DEVICE_TOKEN);
                }

                this.checkpoint(ApnsPushNotificationDecoderState.TOKEN);

                break;
            }

            case TOKEN: {
                in.readBytes(this.token);
                this.checkpoint(ApnsPushNotificationDecoderState.PAYLOAD_LENGTH);

                break;
            }

            case PAYLOAD_LENGTH: {
                final int payloadSize = in.readShort() & 0x0000FFFF;

                if (payloadSize > MAX_PAYLOAD_SIZE || payloadSize == 0) {
                    this.reportErrorAndCloseConnection(context,
                            this.sequenceNumber,
                            DeliveryError.INVALID_PAYLOAD_SIZE);
                } else {
                    this.payloadBytes = new byte[payloadSize];
                    this.checkpoint(ApnsPushNotificationDecoderState.PAYLOAD);
                }

                break;
            }

            case PAYLOAD: {
                in.readBytes(this.payloadBytes);

                final ApnsNotification pushNotification = new EnhancedApnsNotification(
                        this.sequenceNumber,
                        (int) (this.expiration.getTime() / 1000), this.token,
                        this.payloadBytes);

                out.add(pushNotification);
                this.checkpoint(ApnsPushNotificationDecoderState.OPCODE);

                break;
            }
            }
        }

        private void reportErrorAndCloseConnection(
                final ChannelHandlerContext context, final int notificationId,
                final DeliveryError errorCode) {
            context.writeAndFlush(new DeliveryResult(errorCode, notificationId))
                    .addListener(new GenericFutureListener<ChannelFuture>() {

                        @Override
                        public void operationComplete(ChannelFuture future) {
                            context.close();
                        }
                    });
        }
    }

    private class ApnsErrorEncoder extends MessageToByteEncoder<DeliveryResult> {

        private static final byte ERROR_COMMAND = 8;

        @Override
        protected void encode(final ChannelHandlerContext context,
                final DeliveryResult DeliveryResult, final ByteBuf out) {
            out.writeByte(ERROR_COMMAND);
            out.writeByte(DeliveryResult.getError().code());
            out.writeInt(DeliveryResult.getId());
        }
    }

    private class MockApnsServerHandler extends
            SimpleChannelInboundHandler<ApnsNotification> {

        private final MockApnsServer server;

        private boolean rejectFutureMessages = false;

        public MockApnsServerHandler(final MockApnsServer server) {
            this.server = server;
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext context,
                ApnsNotification receivedNotification) throws Exception {
            System.out.println("RECEIVED " + receivedNotification);
            if (!this.rejectFutureMessages) {
                final DeliveryResult rejection = this.server
                        .handleReceivedNotification(receivedNotification);

                if (rejection != null) {

                    this.rejectFutureMessages = true;

                    context.writeAndFlush(rejection).addListener(
                            new GenericFutureListener<ChannelFuture>() {

                                @Override
                                public void operationComplete(
                                        final ChannelFuture future) {
                                    setupNextNotificationsList();
                                    context.close();
                                }

                            });
                }
            }
        }
    }

    public MockApnsServer(final int port, final SSLContext sslContext) {
        this.port = port;
        this.sslContext = sslContext;
        this.countdownLatches = new Vector<CountDownLatch>();
        setupNextNotificationsList();
    }

    public void start() throws InterruptedException {
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();

        final ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(bossGroup, workerGroup);
        bootstrap.channel(NioServerSocketChannel.class);

        final MockApnsServer server = this;

        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel)
                    throws Exception {
                SSLEngine engine = sslContext.createSSLEngine();
                engine.setUseClientMode(false);
                channel.pipeline().addLast("ssl", new SslHandler(engine));
                // channel.pipeline().addLast("log", new
                // LoggingHandler(LogLevel.INFO));
                channel.pipeline().addLast("encoder", new ApnsErrorEncoder());
                channel.pipeline().addLast("decoder",
                        new ApnsPushNotificationDecoder());
                channel.pipeline().addLast("handler",
                        new MockApnsServerHandler(server));
            }

        });

        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

        bootstrap.bind(this.port).sync();
    }

    public void shutdown() throws InterruptedException {
        this.workerGroup.shutdownGracefully();
        this.bossGroup.shutdownGracefully();
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
        this.errorCode = errorCode;
        this.idToFail = idToFail;
        this.failWhenReceive = failWhenReceive;
    }

    protected DeliveryResult handleReceivedNotification(
            final ApnsNotification receivedNotification) {
        addReceivedNotification(receivedNotification);

        for (final CountDownLatch latch : this.countdownLatches) {
            latch.countDown();
        }
        synchronized (this) {
            if (failWhenReceive != null
                    && failWhenReceive == receivedNotification.getIdentifier()) {
                DeliveryResult result = new DeliveryResult(errorCode, idToFail);
                System.err.println("Causing failure...");

                errorCode = null;
                failWhenReceive = null;
                idToFail = null;
                return result;
            } else
                return null;
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
}