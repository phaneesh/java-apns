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
import java.util.concurrent.RejectedExecutionException;
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
import com.notnoop.apns.internal.netty.util.concurrency.AbstractPriorityRunnableImpl;
import com.notnoop.apns.internal.netty.util.concurrency.PrioritizedExecutorService;
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

    private final ExecutorService executorService = new PrioritizedExecutorService(
            1, 1, TimeUnit.SECONDS);

    private final Object lockSendMessage = new Object();

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
        LOGGER.debug("Shutdown of single-thread executor service...");
        executorService.shutdown();
        try {
            LOGGER.debug("Waiting termination of single-thread executor service...");
            executorService.awaitTermination(30, TimeUnit.SECONDS);
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
        synchronized (lockSendMessage) {
            int attempts = 0;
            while (true) {
                attempts++;
                try {
                    channelProvider.runWithChannel(new WithChannelAction() {
                        @Override
                        public void perform(Channel channel) throws Exception {
                            synchronized (cacheStore) {
                                channel.writeAndFlush(m).sync();
                                cacheStore.add(m);
                            }
                            delegate.messageSent(m, fromBuffer);
                            LOGGER.debug("Message \"{}\" sent (fromBuffer={})",
                                    m, fromBuffer);
                            if (!fromBuffer)
                                drainBuffer();
                        }
                    });
                    break;
                } catch (Exception e) {
                    if (attempts > RETRIES) {
                        delegate.messageSendFailed(m, e);
                        Utilities.wrapAndThrowAsRuntimeException(e);
                    }
                    LOGGER.info("Failed to send message " + m + " (fromBuffer="
                            + fromBuffer + ", attempts=" + attempts
                            + " trying again after delay... (r", e);
                    Utilities.sleep(DELAY_IN_MS);
                }
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
            executorService.submit(new AbstractPriorityRunnableImpl(1) {

                @Override
                public void priorityRun() {
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
        drainBuffer();
    }

    @Override
    public void onDeliveryResult(final DeliveryResult msg) {
        executorService.submit(new AbstractPriorityRunnableImpl(0) {
            @Override
            public void priorityRun() {
                Integer newCacheLength = null;
                try {
                    synchronized (cacheStore) {
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
                    }
                } finally {
                    if (newCacheLength != null) {
                        delegate.cacheLengthExceeded(newCacheLength);
                    }
                }
            }
        });
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
