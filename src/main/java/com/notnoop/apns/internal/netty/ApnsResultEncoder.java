package com.notnoop.apns.internal.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import com.notnoop.apns.DeliveryResult;

@Sharable
public class ApnsResultEncoder extends MessageToByteEncoder<DeliveryResult> {

    @Override
    public void encode(ChannelHandlerContext ctx, DeliveryResult msg,
            ByteBuf out) throws Exception {
        out.writeByte(8);
        out.writeByte(msg.getError().code());
        out.writeInt(msg.getId());
    }

}
