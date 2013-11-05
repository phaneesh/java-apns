package com.notnoop.apns.internal.netty.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractChannelProvider implements ChannelProvider {

    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private ChannelHandlersProvider channelHandlersProvider;

    private ChannelClosedListener channelClosedListener;

    @Override
    public void setChannelHandlersProvider(
            ChannelHandlersProvider channelHandlersProvider) {
        this.channelHandlersProvider = channelHandlersProvider;
    }

    public ChannelHandlersProvider getChannelHandlersProvider() {
        return channelHandlersProvider;
    }

    public ChannelClosedListener getChannelClosedListener() {
        return channelClosedListener;
    }

    @Override
    public void setChannelClosedListener(
            ChannelClosedListener channelClosedListener) {
        this.channelClosedListener = channelClosedListener;
    }

}
