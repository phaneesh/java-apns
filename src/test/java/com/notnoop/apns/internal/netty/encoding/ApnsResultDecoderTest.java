package com.notnoop.apns.internal.netty.encoding;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.notnoop.apns.DeliveryError;
import com.notnoop.apns.DeliveryResult;
import com.notnoop.apns.internal.netty.encoding.ApnsResultDecoder;

public class ApnsResultDecoderTest {

    ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
    ApnsResultDecoder decoder = new ApnsResultDecoder();

    @Test
    public void testDecode() throws Exception {
        ByteBuf buf = new PooledByteBufAllocator().buffer();
        buf.writeByte(8);
        buf.writeByte(1);
        buf.writeInt(1234);
        DeliveryResult result = (DeliveryResult) decoder.decode(ctx, buf);
        Assert.assertEquals(result.getError(), DeliveryError.ofCode(1));
        Assert.assertEquals(result.getId(), 1234);
    }
}
