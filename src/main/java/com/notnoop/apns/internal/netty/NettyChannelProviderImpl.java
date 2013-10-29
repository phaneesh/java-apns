package com.notnoop.apns.internal.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.ssl.SslHandler;

import java.io.IOException;

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

    private volatile ChannelFuture channelFuture;

    public NettyChannelProviderImpl(EventLoopGroup eventLoopGroup,
            ReconnectPolicy reconnectPolicy, String host, int port,
            int readTimeout, SSLContext sslContext) {
        this.reconnectPolicy = reconnectPolicy;
        this.host = host;
        this.port = port;
        this.sslContext = sslContext;
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.AUTO_READ, value)
    }

    @Override
    public synchronized Channel getChannel() {
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
                channelFuture = null;
            } catch (InterruptedException e) {
                LOGGER.error("Error while closing connection", e);
            }
        }
    }

    @Override
    public void init() {
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                SSLEngine engine = sslContext.createSSLEngine();
                engine.setUseClientMode(true);
                ch.pipeline().addFirst("ssl", new SslHandler(engine));
                for (ChannelHandler h : NettyChannelProviderImpl.this
                        .getChannelHandlersProvider().getChannelHandlers()) {
                    ch.pipeline().addLast(h);
                }
            }
        });
    }

}
