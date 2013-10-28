package com.notnoop.apns.internal.netty;

import io.netty.channel.Channel;

import java.io.IOException;

public interface ChannelProvider {

    Channel getChannel();

    void close() throws IOException;

    void setChannelConfigurer(ChannelConfigurer channelConfigurer);

    void init();

    public static interface ChannelConfigurer {
        void configure(Channel channel);
    }
}
