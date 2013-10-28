package com.notnoop.apns.internal.netty;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import io.netty.buffer.ByteBuf;

import org.junit.Test;

import com.notnoop.apns.ApnsDelegate;
import com.notnoop.apns.EnhancedApnsNotification;

public class NettyApnsConnectionImplTest {

    private static final int N = 100;

    @Test
    public void testSendMessages() {
        EnhancedApnsNotification[] notifications = new EnhancedApnsNotification[N];
        for (int i = 0; i < N; i++) {
            notifications[i] = new EnhancedApnsNotification(i, 10,
                    "asdf1234asdf5678asdf9012asdf3456".getBytes(),
                    ("hello there notification " + i).getBytes());
        }

        MockChannelProvider channelProvider = mockChannelProvider();

        NettyApnsConnectionImpl conn = new NettyApnsConnectionImpl(
                channelProvider, mock(ApnsDelegate.class));

        for (int i = 0; i < N; i++) {
            conn.sendMessage(notifications[i], false);
        }

        assertEquals(N, channelProvider.getCurrentChannel().outboundMessages()
                .size());

        for (int i = 0; i < N; i++) {
            ByteBuf buf = (ByteBuf) channelProvider.getCurrentChannel()
                    .outboundMessages().poll();
            byte[] expected = notifications[i].marshall();
            byte[] received = new byte[expected.length];
            buf.getBytes(0, received);
            assertArrayEquals(expected, received);
        }
    }

    private MockChannelProvider mockChannelProvider(int... failingIDs) {
        return new MockChannelProvider();
    }

    // @Test
    // public void testSendMessages_failure_at_70() throws InterruptedException
    // {
    // int fails = 70;
    // ApnsResultEncoder resultEncoder = new ApnsResultEncoder();
    // EnhancedApnsNotification[] notifications = new
    // EnhancedApnsNotification[N];
    // for (int i = 0; i < N; i++) {
    // notifications[i] = new EnhancedApnsNotification(i, 10,
    // "asdf1234asdf5678asdf9012asdf3456".getBytes(),
    // ("hello there notification " + i).getBytes());
    // }
    //
    // EmbeddedChannel debugChannel = new EmbeddedChannel();
    // ChannelProvider provider = Mockito.mock(ChannelProvider.class);
    // Mockito.when(provider.getChannel()).thenReturn(debugChannel);
    //
    // NettyApnsConnectionImpl conn = new NettyApnsConnectionImpl(provider,
    // new ApnsDelegateAdapter());
    //
    // debugChannel.pipeline().addLast(new ApnsHandler(conn),
    // new ApnsNotificationEncoder(), new ApnsResultDecoder());
    //
    // DeliveryResult error = new DeliveryResult(DeliveryError.INVALID_TOKEN,
    // fails);
    //
    // for (int i = 0; i < error.getId(); i++) {
    // conn.sendMessage(notifications[i], false);
    // }
    //
    // // Send error
    //
    // ByteBuf buf = debugChannel.alloc().buffer(6);
    // try {
    // resultEncoder.encode(null, error, buf);
    // } catch (Exception e) {
    // throw new RuntimeException(e);
    // }
    // debugChannel.writeInbound(buf);
    // debugChannel.close();
    // debugChannel = getNewEmbeddedChannel(conn);
    // Mockito.verify(conn).onMessageReceived(
    // Matchers.any(ChannelHandlerContext.class), Matchers.eq(error));
    //
    // for (int i = error.getId(); i < N; i++) {
    //
    // }
    //
    // assertEquals(N, debugChannel.outboundMessages().size());
    //
    // for (int i = 0; i < N; i++) {
    // ByteBuf buffer = (ByteBuf) debugChannel.outboundMessages().poll();
    // byte[] expected = notifications[i].marshall();
    // byte[] received = new byte[expected.length];
    // buffer.getBytes(0, received);
    // assertArrayEquals(expected, received);
    // }
    //
    // }
}
