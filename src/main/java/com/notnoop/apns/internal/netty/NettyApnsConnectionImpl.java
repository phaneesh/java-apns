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
import com.notnoop.apns.internal.netty.channel.ChannelProvider.ChannelHandlersProvider;
import com.notnoop.apns.internal.netty.channel.ChannelProvider.WithChannelAction;
import com.notnoop.apns.internal.netty.encoding.ApnsNotificationEncoder;
import com.notnoop.apns.internal.netty.encoding.ApnsResultDecoder;
import com.notnoop.exceptions.ApnsDeliveryErrorException;
import com.notnoop.exceptions.NetworkIOException;

public class NettyApnsConnectionImpl implements ApnsConnection,
        DeliveryResultListener {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(NettyApnsConnectionImpl.class);

    private static final int RETRIES = 3;

    private static final int DELAY_IN_MS = 1000;

    private final ApnsDelegate delegate;
    private final ChannelProvider channelProvider;
    private final CacheStore cacheStore;
    private final ExecutorService executorService = Executors
            .newSingleThreadExecutor();

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

    // This was done in the constructor, but it was impossible to spy the
    // reference to NettyApnsConnectionImpl.this. This is code smell and needs
    // to be addressed... fur to the shake of testability, for now I'll keep it
    // this way...
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

        channelProvider.init();
    }

    @Override
    public synchronized void close() throws IOException {
        executorService.shutdown();
        channelProvider.close();
    }

    @Override
    public synchronized void sendMessage(ApnsNotification m)
            throws NetworkIOException {
        sendMessage(m, false);
    }

    protected synchronized void sendMessage(final ApnsNotification m,
            final boolean fromBuffer) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                channelProvider.runWithChannel(new WithChannelAction() {
                    @Override
                    public void perform(Channel channel) throws Exception {
                        cacheStore.add(m);
                        channel.writeAndFlush(m).sync();
                        delegate.messageSent(m, fromBuffer);
                        LOGGER.debug("Message \"{}\" sent (fromBuffer={})", m,
                                fromBuffer);
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

    private void drainBuffer() {
        cacheStore.drain(new Drainer() {
            @Override
            public void process(ApnsNotification notification) {
                sendMessage(notification, true);
            }
        });
    }

    @Override
    public void onDeliveryResult(final DeliveryResult msg) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                synchronized (cacheStore) {
                    Integer newCacheLength = null;
                    try {
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
                    } finally {
                        try {
                            channelProvider.close();
                            drainBuffer();
                            if (newCacheLength != null) {
                                delegate.cacheLengthExceeded(newCacheLength);
                            }
                        } catch (IOException e) {
                            LOGGER.error("I/O Exception while closing", e);
                        }
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
