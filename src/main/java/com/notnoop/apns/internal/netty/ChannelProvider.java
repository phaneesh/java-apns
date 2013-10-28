package com.notnoop.apns.internal.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

import java.io.IOException;
import java.util.List;

public interface ChannelProvider {

    Channel getChannel();

    void close() throws IOException;

    void setChannelHandlersProvider(
            ChannelHandlersProvider channelHandlersProvider);

    void init();

    public static interface ChannelHandlersProvider {
        List<ChannelHandler> getChannelHandlers();
    }
}
