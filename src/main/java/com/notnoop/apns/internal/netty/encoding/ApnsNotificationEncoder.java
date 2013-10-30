package com.notnoop.apns.internal.netty.encoding;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import com.notnoop.apns.ApnsNotification;

@Sharable
public class ApnsNotificationEncoder extends
        MessageToByteEncoder<ApnsNotification> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ApnsNotification msg,
            ByteBuf out) throws Exception {
        out.writeBytes(msg.marshall());
    }
}
