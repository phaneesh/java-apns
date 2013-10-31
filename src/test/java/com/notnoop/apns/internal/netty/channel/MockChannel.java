package com.notnoop.apns.internal.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryError;
import com.notnoop.apns.DeliveryResult;
import com.notnoop.apns.internal.netty.encoding.ApnsResultEncoder;

public class MockChannel extends EmbeddedChannel {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(MockChannel.class);

    private static final ApnsResultEncoder ENCODER = new ApnsResultEncoder();

    private final int failureAt;
    private final DeliveryError error;

    public MockChannel(int failureAt, DeliveryError error,
            ChannelHandler... handlers) {
        super(handlers);
        this.error = error;
        this.failureAt = failureAt;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        ChannelFuture future = super.writeAndFlush(msg);

        if (msg instanceof ApnsNotification) {
            ApnsNotification notification = (ApnsNotification) msg;
            LOGGER.debug("Received message {}", notification);
            if (notification.getIdentifier() == failureAt) {
                ByteBuf buf = alloc().buffer(6);
                buf.retain();
                try {
                    ENCODER.encode(null, new DeliveryResult(error, failureAt),
                            buf);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                writeInbound(buf);
                close();
                buf.release();
            }
        }

        return future;
    }
}
