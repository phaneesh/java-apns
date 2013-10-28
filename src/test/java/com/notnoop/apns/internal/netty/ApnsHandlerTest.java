package com.notnoop.apns.internal.netty;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import io.netty.channel.ChannelHandlerContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.notnoop.apns.DeliveryError;
import com.notnoop.apns.DeliveryResult;

public class ApnsHandlerTest {
    @Mock
    NettyApnsConnectionImpl owner;
    @Mock
    ChannelHandlerContext ctx;

    ApnsHandler apnsHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        apnsHandler = new ApnsHandler(owner);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testChannelRead0() throws Exception {
        DeliveryResult msg = new DeliveryResult(DeliveryError.INVALID_TOKEN,
                1234);
        apnsHandler.channelRead0(ctx, msg);
        verify(owner).onMessageReceived(eq(ctx), eq(msg));
    }

}
