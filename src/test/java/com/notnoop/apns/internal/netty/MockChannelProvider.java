package com.notnoop.apns.internal.netty;

import io.netty.channel.Channel;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.notnoop.apns.DeliveryError;

public class MockChannelProvider extends AbstractChannelProvider {
    private final List<MockChannel> mockChannels = new LinkedList<MockChannel>();
    private volatile MockChannel currentChannel = null;
    private int failureAt = Integer.MIN_VALUE;
    private DeliveryError errorCode = DeliveryError.INVALID_TOKEN;

    @Override
    public synchronized Channel getChannel() {
        if (currentChannel == null || !currentChannel.isOpen()) {
            LOGGER.info("Opening a new channel...");
            currentChannel = new MockChannel(failureAt, errorCode);
            getChannelConfigurer().configure(currentChannel);
            mockChannels.add(currentChannel);
        }
        return currentChannel;
    }

    @Override
    public void close() throws IOException {
        try {
            currentChannel.close().sync();
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while closing", e);
        }
    }

    @Override
    public void init() {

    }

    public MockChannel getCurrentChannel() {
        return currentChannel;
    }

    public List<MockChannel> getMockChannels() {
        return mockChannels;
    }

    public int getFailureAt() {
        return failureAt;
    }

    public DeliveryError getErrorCode() {
        return errorCode;
    }

    public void setFailureAt(int failureAt) {
        this.failureAt = failureAt;
    }

    public void setErrorCode(DeliveryError errorCode) {
        this.errorCode = errorCode;
    }

}
