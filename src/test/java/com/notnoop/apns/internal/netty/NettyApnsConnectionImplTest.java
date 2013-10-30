package com.notnoop.apns.internal.netty;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import io.netty.buffer.ByteBuf;

import org.junit.Test;

import com.notnoop.apns.ApnsDelegate;
import com.notnoop.apns.ApnsDelegateAdapter;
import com.notnoop.apns.DeliveryError;
import com.notnoop.apns.DeliveryResult;
import com.notnoop.apns.EnhancedApnsNotification;

public class NettyApnsConnectionImplTest {
    ApnsResultEncoder resultEncoder = new ApnsResultEncoder();

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
                channelProvider, mock(ApnsDelegate.class), 200, true);
        conn.init();

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

    @SuppressWarnings("resource")
    @Test
    public void testSendMessages_failure_at_70() throws InterruptedException {
        int failAt = 70;
        DeliveryError failure = DeliveryError.MISSING_DEVICE_TOKEN;
        EnhancedApnsNotification[] notifications = new EnhancedApnsNotification[N];
        for (int i = 0; i < N; i++) {
            notifications[i] = new EnhancedApnsNotification(i, 10,
                    "asdf1234asdf5678asdf9012asdf3456".getBytes(),
                    ("hello there notification " + i).getBytes());
        }

        MockChannelProvider provider = mockChannelProvider(failAt);
        provider.setErrorCode(DeliveryError.MISSING_DEVICE_TOKEN);
        provider.setFailureAt(failAt);
        provider.init();

        NettyApnsConnectionImpl conn = new NettyApnsConnectionImpl(provider,
                new ApnsDelegateAdapter(), 200, true);
        conn = spy(conn);

        conn.init();

        for (int i = 0; i < N; i++) {
            conn.sendMessage(notifications[i], false);
        }
        // Verify an error was sent...
        verify(conn).onDeliveryResult(eq(new DeliveryResult(failure, failAt)));
        // Verify there have been two channels in mock provider...
        assertEquals(2, provider.getMockChannels().size());
        // Verify the content in both channels is as expected...
        assertEquals(failAt + 1, provider.getMockChannels().get(0)
                .outboundMessages().size());
        assertEquals(N - failAt - 1, provider.getMockChannels().get(1)
                .outboundMessages().size());

    }
}
