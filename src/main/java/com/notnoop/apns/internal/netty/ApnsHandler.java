package com.notnoop.apns.internal.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.DeliveryResult;

public class ApnsHandler extends SimpleChannelInboundHandler<DeliveryResult> {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(ApnsHandler.class);

    private final NettyApnsConnectionImpl owner;

    public ApnsHandler(NettyApnsConnectionImpl owner) {
        super(DeliveryResult.class);
        this.owner = owner;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, DeliveryResult msg)
            throws Exception {
        LOGGER.debug("Received message: {}", msg);
        owner.onMessageReceived(ctx, msg);
    }
    
    

}
