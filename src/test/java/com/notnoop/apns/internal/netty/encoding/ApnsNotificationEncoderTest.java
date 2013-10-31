package com.notnoop.apns.internal.netty.encoding;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.notnoop.apns.EnhancedApnsNotification;
import com.notnoop.apns.internal.netty.encoding.ApnsNotificationEncoder;

public class ApnsNotificationEncoderTest {

    ChannelHandlerContext context = Mockito.mock(ChannelHandlerContext.class);

    ApnsNotificationEncoder encoder = new ApnsNotificationEncoder();

    @Test
    public void testEncode() throws Exception {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator();
        ByteBuf buf = allocator.buffer();
        EnhancedApnsNotification n = new EnhancedApnsNotification(1234, 10,
                "asdf1234asdf5678asdf9012asdf3456".getBytes(),
                "hello there".getBytes());

        encoder.encode(context, n, buf);
        byte[] expected = n.marshall();
        byte[] obtained = new byte[expected.length];
        buf.getBytes(0, obtained);
        Assert.assertArrayEquals(expected, obtained);
    }
}
