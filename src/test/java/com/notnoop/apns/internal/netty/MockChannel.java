package com.notnoop.apns.internal.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryError;
import com.notnoop.apns.DeliveryResult;

public class MockChannel extends EmbeddedChannel {

    private static final ApnsResultEncoder ENCODER = new ApnsResultEncoder();

    private final int failureAt;
    private final DeliveryError error;

    public MockChannel(int failureAt, DeliveryError error) {
        super(new LoggingHandler(LogLevel.DEBUG));
        this.error = error;
        this.failureAt = failureAt;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        ChannelFuture future = super.writeAndFlush(msg);

        if (msg instanceof ApnsNotification) {
            if (((ApnsNotification) msg).getIdentifier() == failureAt) {
                ByteBuf buf = alloc().buffer(6);
                try {
                    ENCODER.encode(null, new DeliveryResult(error, failureAt),
                            buf);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                writeInbound(buf);
                close();
            }
        }

        return future;
    }
}
