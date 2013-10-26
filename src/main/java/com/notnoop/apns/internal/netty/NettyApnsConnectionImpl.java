package com.notnoop.apns.internal.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.ApnsDelegate;
import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryResult;
import com.notnoop.apns.ReconnectPolicy;
import com.notnoop.apns.internal.ApnsConnection;
import com.notnoop.apns.internal.ApnsConnectionImpl;
import com.notnoop.apns.internal.Utilities;
import com.notnoop.exceptions.ApnsDeliveryErrorException;
import com.notnoop.exceptions.NetworkIOException;

public class NettyApnsConnectionImpl implements ApnsConnection {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ApnsConnectionImpl.class);
    private final ReconnectPolicy reconnectPolicy;
    private final Queue<ApnsNotification> cachedNotifications,
            notificationsBuffer;
    private final ApnsDelegate delegate;
    private final Bootstrap bootstrap;

    private EventLoopGroup workerGroup;

    private int cacheLength;

    private volatile ChannelFuture channelFuture;

    // private final int readTimeout;

    private final int port;
    private final String host;

    public NettyApnsConnectionImpl(String host, int port,
            final SSLContext sslContext, ApnsDelegate delegate,
            ReconnectPolicy reconnectPolicy, EventLoopGroup workerGroup,
            int readTimeout) {
        cachedNotifications = new ConcurrentLinkedQueue<ApnsNotification>();
        notificationsBuffer = new ConcurrentLinkedQueue<ApnsNotification>();
        this.workerGroup = workerGroup;
        // this.readTimeout = readTimeout;
        this.delegate = delegate;
        this.host = host;
        this.port = port;
        this.reconnectPolicy = reconnectPolicy;
        try {
            bootstrap = new Bootstrap();
            bootstrap.group(workerGroup);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            bootstrap
                    .option(ChannelOption.AIO_READ_TIMEOUT, (long) readTimeout);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast("ssl",
                            new SslHandler(sslContext.createSSLEngine()));
                    ch.pipeline().addLast("encoder",
                            new ApnsNotificationEncoder());
                    ch.pipeline().addLast("decoder", new ApnsResultDecoder());
                    ch.pipeline().addLast("handler",
                            new ApnsHandler(NettyApnsConnectionImpl.this));
                }
            });
            // trigger connection
            // channel();
            // Wait until the connection is closed.
            // // f.channel().closeFuture().sync();
        } finally {
            //
        }
    }

    protected synchronized Channel channel() {
        // Start the client.
        // channelFuture = bootstrap.connect(host, port).sync().ch
        if (reconnectPolicy.shouldReconnect() && channelFuture != null) {
            try {
                channelFuture.channel().close().sync();
            } catch (Throwable t) {
                LOGGER.error("Error while closing connection", t);
            } finally {
                channelFuture = null;
            }
        }
        if (channelFuture == null || !channelFuture.channel().isActive()) {
            try {
                channelFuture = bootstrap.connect(host, port).sync();
            } catch (InterruptedException e) {
                LOGGER.error("Error while connecting", e);
            }
            reconnectPolicy.reconnected();
            LOGGER.debug("APNS reconnected");
        }
        return channelFuture.channel();
    }

    @Override
    public synchronized void close() throws IOException {
        if (channelFuture.channel().isOpen()) {
            try {
                channelFuture.channel().close().sync();
            } catch (InterruptedException e) {
                LOGGER.error("Error while closing connection", e);
            }
        }
        workerGroup.shutdownGracefully();
    }

    private void cacheNotification(ApnsNotification notification) {
        cachedNotifications.add(notification);
        while (cachedNotifications.size() > cacheLength) {
            cachedNotifications.poll();
            LOGGER.debug("Removing notification from cache " + notification);
        }
    }

    @Override
    public synchronized void sendMessage(ApnsNotification m)
            throws NetworkIOException {
        sendMessage(m, false);
    }

    protected synchronized void sendMessage(ApnsNotification m,
            boolean fromBuffer) {
        sendMessage(channel(), m, fromBuffer);
    }

    protected synchronized void sendMessage(Channel channel,
            ApnsNotification m, boolean fromBuffer) {

        while (true) {
            try {

                channel.writeAndFlush(m);

                cacheNotification(m);

                delegate.messageSent(m, fromBuffer);
                LOGGER.debug("Message \"{}\" sent", m);
                drainBuffer(channel);
                break;
            } catch (Exception e) {
                Utilities.wrapAndThrowAsRuntimeException(e);
            }
        }
    }

    private void drainBuffer(Channel channel) {
        if (!notificationsBuffer.isEmpty()) {
            sendMessage(channel, notificationsBuffer.poll(), true);
        }
    }

    public void onMessageReceived(ChannelHandlerContext ctx, DeliveryResult msg) {
        Queue<ApnsNotification> tempCache = new LinkedList<ApnsNotification>();
        ApnsNotification notification = null;
        boolean foundNotification = false;

        while ((notification = cachedNotifications.poll()) != null) {
            notification = cachedNotifications.poll();

            if (notification.getIdentifier() == msg.getId()) {
                foundNotification = true;
                break;
            }
            tempCache.add(notification);
        }

        if (foundNotification) {
            delegate.messageSendFailed(notification,
                    new ApnsDeliveryErrorException(msg.getError()));
        } else {
            cachedNotifications.addAll(tempCache);
            LOGGER.warn("Received error for message "
                    + "that wasn't in the cache...");
            delegate.messageSendFailed(null,
                    new ApnsDeliveryErrorException(msg.getError()));
        }

        int resendSize = 0;
        while (!cachedNotifications.isEmpty()) {
            resendSize++;
            notificationsBuffer.add(cachedNotifications.poll());
        }
        delegate.notificationsResent(resendSize);
        delegate.connectionClosed(msg.getError(), msg.getId());
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

    @Override
    public void setCacheLength(int cacheLength) {
        this.cacheLength = cacheLength;
    }

    @Override
    public int getCacheLength() {
        return cacheLength;
    }

}
