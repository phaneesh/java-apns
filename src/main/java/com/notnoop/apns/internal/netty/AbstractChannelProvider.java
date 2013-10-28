package com.notnoop.apns.internal.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractChannelProvider implements ChannelProvider {

    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private ChannelConfigurer channelConfigurer;

    public ChannelConfigurer getChannelConfigurer() {
        return channelConfigurer;
    }

    @Override
    public void setChannelConfigurer(ChannelConfigurer channelConfigurer) {
        this.channelConfigurer = channelConfigurer;
    }

}
