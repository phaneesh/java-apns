package com.notnoop.apns.internal.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractChannelProvider implements ChannelProvider {

    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private ChannelHandlersProvider channelHandlersProvider;

    @Override
    public void setChannelHandlersProvider(
            ChannelHandlersProvider channelHandlersProvider) {
        this.channelHandlersProvider = channelHandlersProvider;
    }

    public ChannelHandlersProvider getChannelHandlersProvider() {
        return channelHandlersProvider;
    }

}
