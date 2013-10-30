package com.notnoop.apns.internal.netty.encoding;

import static org.junit.Assert.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;

import org.junit.Test;
import org.mockito.Mockito;

import com.notnoop.apns.DeliveryError;
import com.notnoop.apns.DeliveryResult;
import com.notnoop.apns.internal.netty.encoding.ApnsResultDecoder;
import com.notnoop.apns.internal.netty.encoding.ApnsResultEncoder;

public class ApnsResultEncoderTest {
    ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
    ApnsResultDecoder decoder = new ApnsResultDecoder();
    ApnsResultEncoder encoder = new ApnsResultEncoder();

    @Test
    public void testEncode() throws Exception {
        DeliveryResult result = new DeliveryResult(DeliveryError.ofCode(1),
                123921392);
        ByteBuf buffer = new PooledByteBufAllocator().buffer();

        encoder.encode(ctx, result, buffer);
        DeliveryResult result2 = (DeliveryResult) decoder.decode(ctx, buffer);
        assertEquals(result, result2);
    }
}
