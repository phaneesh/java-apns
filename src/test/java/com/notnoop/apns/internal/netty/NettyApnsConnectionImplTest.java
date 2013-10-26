package com.notnoop.apns.internal.netty;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;

import org.junit.Test;

import com.notnoop.apns.ApnsDelegateAdapter;
import com.notnoop.apns.EnhancedApnsNotification;
import com.notnoop.apns.internal.ReconnectPolicies;
import com.notnoop.apns.utils.FixedCertificates;

public class NettyApnsConnectionImplTest {
    @Test
    public void testSendMessage() {
        EnhancedApnsNotification n = new EnhancedApnsNotification(1234, 10,
                "asdf1234asdf5678asdf9012asdf3456".getBytes(),
                "hello there".getBytes());
        NettyApnsConnectionImpl conn = new NettyApnsConnectionImpl("127.0.0.1",
                1234, FixedCertificates.clientContext(),
                new ApnsDelegateAdapter(),
                new ReconnectPolicies.EveryHalfHour(), new NioEventLoopGroup(),
                100000);
        EmbeddedChannel debugChannel = new EmbeddedChannel(
                new ApnsHandler(conn), new ApnsNotificationEncoder(),
                new ApnsResultDecoder());
        conn.sendMessage(debugChannel, n, false);
    }
}
