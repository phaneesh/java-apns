package com.notnoop.apns.internal.netty.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.notnoop.apns.ReconnectPolicy;

// TODO test
public class NettyChannelProviderImpl extends AbstractChannelProvider {

    private final ReconnectPolicy reconnectPolicy;
    private final SSLContext sslContext;
    private final Bootstrap bootstrap;
    private final String host;

    private final int port;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final AtomicReference<ChannelFuture> channelFutureReference = new AtomicReference<>();
    private final EventLoopGroup eventLoopGroup;

    public NettyChannelProviderImpl(EventLoopGroup eventLoopGroup,
            ReconnectPolicy reconnectPolicy, String host, int port,
            int readTimeout, SSLContext sslContext) {
        this.reconnectPolicy = reconnectPolicy;
        this.host = host;
        this.port = port;
        this.sslContext = sslContext;
        this.eventLoopGroup = eventLoopGroup;
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
    }

    public ChannelFuture getCurrentChannelFuture() {
        return channelFutureReference.get();
    }

    // @Override
    public Channel getChannel() {
        ChannelFuture channelFuture = channelFutureReference.get();
        // Start the client.
        // channelFuture = bootstrap.connect(host, port).sync().ch
        if (reconnectPolicy.shouldReconnect() && channelFuture != null) {
            try {
                close();
            } catch (Throwable t) {
                LOGGER.error("Error while closing connection", t);
            }
        }
        if (channelFuture == null || !channelFuture.channel().isActive()) {
            try {
                channelFuture = bootstrap.connect(host, port).sync();
                channelFutureReference.set(channelFuture);
                reconnectPolicy.reconnected();
                LOGGER.debug("APNS reconnected");
            } catch (InterruptedException e) {
                LOGGER.error("Error while connecting", e);
            }
        }
        return channelFuture.channel();
    }

    @Override
    public void close() throws IOException {
        final ChannelFuture channelFuture = channelFutureReference
                .getAndSet(null);
        Channel channel = null;
        if (channelFuture != null
                && (channel = channelFuture.channel()) != null) {
            try {
                LOGGER.debug("Sync-closing channel...");
                channel.close().sync();
                LOGGER.debug("Channel closed");
            } catch (InterruptedException e) {
                LOGGER.error("Error while closing", e);
            }
        }
    }

    @Override
    public void close(Channel channel) throws IOException {
        final ChannelFuture channelFuture = channelFutureReference.get();
        if (channelFuture != null && channelFuture.channel() == channel) {
            channelFutureReference.set(null);
        }

        try {
            LOGGER.debug("Sync-closing channel...");
            channel.close().sync();
            LOGGER.debug("Channel closed");
        } catch (InterruptedException e) {
            LOGGER.error("Error while closing", e);
        }
    }

    @Override
    public void runWithChannel(WithChannelAction action) throws Exception {
        Channel channel = getChannel();
        try {
            action.perform(channel);
        } catch (Exception e) {
            channel.read();
            throw e;
        }
    }

    @Override
    public void init() {
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(final SocketChannel ch) throws Exception {
                SSLEngine engine = sslContext.createSSLEngine();
                engine.setUseClientMode(true);
                ch.pipeline().addFirst("ssl", new SslHandler(engine));
                for (ChannelHandler h : NettyChannelProviderImpl.this
                        .getChannelHandlersProvider().getChannelHandlers()) {
                    ch.pipeline().addLast(h);
                }
                ch.config().setAutoClose(false);
                ch.closeFuture().addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(ChannelFuture future)
                            throws Exception {
                        getChannelClosedListener().onChannelClosed(ch);
                    }
                });
            }
        });
    }

}
