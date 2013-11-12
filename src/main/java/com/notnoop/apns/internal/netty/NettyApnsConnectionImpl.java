package com.notnoop.apns.internal.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.ApnsDelegate;
import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryResult;
import com.notnoop.apns.internal.ApnsConnection;
import com.notnoop.apns.internal.Utilities;
import com.notnoop.apns.internal.netty.cache.CacheStore;
import com.notnoop.apns.internal.netty.cache.CacheStore.Drainer;
import com.notnoop.apns.internal.netty.channel.ChannelProvider;
import com.notnoop.apns.internal.netty.channel.ChannelProvider.ChannelClosedListener;
import com.notnoop.apns.internal.netty.channel.ChannelProvider.ChannelHandlersProvider;
import com.notnoop.apns.internal.netty.channel.ChannelProvider.WithChannelAction;
import com.notnoop.apns.internal.netty.encoding.ApnsNotificationEncoder;
import com.notnoop.apns.internal.netty.encoding.ApnsResultDecoder;
import com.notnoop.exceptions.ApnsDeliveryErrorException;
import com.notnoop.exceptions.NetworkIOException;

public class NettyApnsConnectionImpl implements ApnsConnection,
        DeliveryResultListener, ChannelClosedListener {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(NettyApnsConnectionImpl.class);

    private static final int RETRIES = 3;
    private static final int DELAY_IN_MS = 1000;

    private final ApnsDelegate delegate;
    private final ChannelProvider channelProvider;
    private final CacheStore cacheStore;

    private final ExecutorService drainBufferExecutorService = Executors
            .newSingleThreadExecutor();
    private final ExecutorService deliveryResultExecutorService = Executors
            .newSingleThreadExecutor();

    private final Object lockSendMessage = new Object();

    // This semaphore is used to control when is allowed to send messages to the
    // channel, it will be disallowed when a response is received from the APNS
    // server (onDeliveryResult is called) and allowed when the cache is moved
    // to the buffer to continue with new connection, avoid the possibility to
    // send a message twice to the APNS server.
    private final Semaphore allowSendSemaphore = new Semaphore(1, true);

    // This semaphore is used to avoid a race condition when a message is added
    // to the cache after sent to the channel and an error response is received
    // from the APNS server, it ensures the message will be added to the buffer
    // to be resent in the draining operation.
    private final Semaphore accessCacheStoreSemaphore = new Semaphore(1, true);

    public NettyApnsConnectionImpl(ChannelProvider channelProvider,
            ApnsDelegate delegate, CacheStore cacheStore) {
        this.delegate = delegate;
        this.channelProvider = channelProvider;
        this.cacheStore = cacheStore;
    }

    @Override
    public void setCacheLength(int cacheLength) {
        cacheStore.setCacheLength(cacheLength);
    }

    @Override
    public int getCacheLength() {
        return cacheStore.getCacheLength();
    }

    public void init() {
        channelProvider
                .setChannelHandlersProvider(new ChannelHandlersProvider() {
                    @Override
                    public List<ChannelHandler> getChannelHandlers() {
                        return Arrays.<ChannelHandler> asList(
                                new LoggingHandler(LogLevel.DEBUG),
                                new ApnsNotificationEncoder(),
                                new ApnsResultDecoder(), new ApnsHandler(
                                        NettyApnsConnectionImpl.this));
                    }
                });
        channelProvider.setChannelClosedListener(this);
        channelProvider.init();
    }

    @Override
    public synchronized void close() throws IOException {
        LOGGER.debug("Closing netty APNS connection");
        LOGGER.debug("Issuing drainBuffer request...");
        drainBuffer();
        // LOGGER.debug("1st close of channel...");
        // channelProvider.close();
        LOGGER.debug("Shutdown of single-thread executor service for drain buffer...");
        drainBufferExecutorService.shutdown();
        try {
            LOGGER.debug("Waiting termination of single-thread executor service for drain buffer...");
            drainBufferExecutorService.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error("Termination did not complete in 30 seconds");
        }
        LOGGER.debug("Shutdown of single-thread executor service for handling delivery results...");
        deliveryResultExecutorService.shutdown();
        try {
            LOGGER.debug("Waiting termination of single-thread executor service for handling delivery results...");
            deliveryResultExecutorService
                    .awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error("Termination did not complete in 30 seconds");
        }

        LOGGER.debug("2nd close of channel...");
        channelProvider.close();

    }

    @Override
    public void sendMessage(ApnsNotification m) throws NetworkIOException {
        sendMessage(m, false);
    }

    protected void sendMessage(final ApnsNotification m,
            final boolean fromBuffer) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                synchronized (lockSendMessage) {
                    channelProvider.runWithChannel(new WithChannelAction() {
                        @Override
                        public void perform(Channel channel) throws Exception {
                            try {
                                LOGGER.debug("Acquiring allowSendSemaphore in sendMessage");
                                allowSendSemaphore.acquire();
                                LOGGER.debug("Acquired allowSendSemaphore in sendMessage");
                                allowSendSemaphore.release();
                                LOGGER.debug("Released allowSendSemaphore in sendMessage");
                                LOGGER.debug("Acquiring accessCacheStoreSemaphore in sendMessage");
                                accessCacheStoreSemaphore.acquire();
                                LOGGER.debug("Acquired accessCacheStoreSemaphore in sendMessage");
                                channel.writeAndFlush(m).sync();
                                cacheStore.add(m);
                            } finally {
                                accessCacheStoreSemaphore.release();
                                LOGGER.debug("Released accessCacheStoreSemaphore in sendMessage");
                            }

                            delegate.messageSent(m, fromBuffer);
                            LOGGER.debug("Message \"{}\" sent (fromBuffer={})",
                                    m, fromBuffer);
                        }
                    });
                }
                break;
            } catch (Exception e) {
                if (attempts > RETRIES) {
                    delegate.messageSendFailed(m, e);
                    Utilities.wrapAndThrowAsRuntimeException(e);
                }
                LOGGER.info("Failed to send message " + m + " (fromBuffer="
                        + fromBuffer + ", attempts=" + attempts
                        + " trying again after delay...)", e);
                Utilities.sleep(DELAY_IN_MS);
            }
        }
    }

    // We don't use this implementation that sends the message asynchronously,
    // we want this to be sync
    // @Override
    // public void sendMessage(final ApnsNotification m) throws
    // NetworkIOException {
    // executorService.submit(new Runnable() {
    // @Override
    // public void run() {
    // sendMessage(m, false, 0);
    // }
    // });
    // }
    //
    // protected void sendMessage(final ApnsNotification m,
    // final boolean fromBuffer, final int retries) {
    //
    // channelProvider.runWithChannel(new WithChannelAction() {
    // @Override
    // public void perform(Channel channel) {
    // cacheStore.add(m);
    // channel.writeAndFlush(m).addListener(
    // new ChannelFutureListener() {
    //
    // @Override
    // public void operationComplete(ChannelFuture future)
    // throws Exception {
    // if (future.isSuccess()) {
    // delegate.messageSent(m, fromBuffer);
    // LOGGER.debug(
    // "Message \"{}\" sent (fromBuffer={})",
    // m, fromBuffer);
    // if (!fromBuffer)
    // drainBuffer();
    // } else {
    // if (retries > RETRIES) {
    // LOGGER.error("Max retries for {}", m);
    // }
    // executorService.submit(new Runnable() {
    //
    // @Override
    // public void run() {
    // sendMessage(m, fromBuffer,
    // retries + 1);
    // }
    // });
    // }
    // }
    // });
    //
    // }
    // });
    //
    // }

    private void drainBuffer() {
        try {
            drainBufferExecutorService.submit(new Runnable() {

                @Override
                public void run() {
                    LOGGER.debug("Draining buffer of notifications that need to be resent");
                    cacheStore.drain(new Drainer() {
                        @Override
                        public void process(ApnsNotification notification) {
                            sendMessage(notification, true);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Could not drain buffer, connection must be shutdown");
        }
    }

    @Override
    public void onChannelClosed(Channel ch) {
        LOGGER.debug("Channel was closed");
    }

    @Override
    public void onDeliveryResult(final DeliveryResult msg) {
        LOGGER.debug("Acquiring allowSendSemaphore in onDeliveryResult");
        allowSendSemaphore.acquireUninterruptibly();
        LOGGER.debug("Acquired allowSendSemaphore in onDeliveryResult");
        try {
            deliveryResultExecutorService.submit(new Runnable() {

                @Override
                public void run() {
                    Integer newCacheLength = null;
                    try {
                        LOGGER.debug("Acquiring accessCacheStoreSemaphore in onDeliveryResult");
                        accessCacheStoreSemaphore.acquireUninterruptibly();
                        LOGGER.debug("Acquired accessCacheStoreSemaphore in onDeliveryResult");
                        Queue<ApnsNotification> tempCache = new LinkedList<ApnsNotification>();
                        ApnsNotification notification = cacheStore
                                .removeAllBefore(msg, tempCache);

                        if (notification != null) {
                            delegate.messageSendFailed(
                                    notification,
                                    new ApnsDeliveryErrorException(msg
                                            .getError()));
                        } else {
                            LOGGER.warn("Received error for message that wasn't in the cache...");
                            cacheStore.addAll(tempCache);
                            newCacheLength = cacheStore
                                    .resizeCacheIfNeeded(tempCache.size());

                            delegate.messageSendFailed(
                                    null,
                                    new ApnsDeliveryErrorException(msg
                                            .getError()));
                        }

                        delegate.notificationsResent(cacheStore
                                .moveCacheToBuffer());
                        delegate.connectionClosed(msg.getError(), msg.getId());
                        drainBuffer();
                    } finally {
                        if (newCacheLength != null) {
                            delegate.cacheLengthExceeded(newCacheLength);
                        }
                        accessCacheStoreSemaphore.release();
                        LOGGER.debug("Released accessCacheStoreSemaphore in onDeliveryResult");
                        allowSendSemaphore.release();
                        LOGGER.debug("Released allowSendSemaphore in onDeliveryResult");
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Could not handle delivery result, connection must be shutdown");
            allowSendSemaphore.release();
            LOGGER.debug("Released allowSendSemaphore in onDeliveryResult");
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            allowSendSemaphore.release();
            LOGGER.debug("Released allowSendSemaphore in onDeliveryResult");
        }

    }

    @Override
    public void testConnection() throws NetworkIOException {
        // TODO Auto-generated method stub

    }

    @Override
    public ApnsConnection copy() {
        // TODO Auto-generated method stub
        return null;
    }

}
