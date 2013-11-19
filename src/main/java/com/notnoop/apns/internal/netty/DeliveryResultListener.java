package com.notnoop.apns.internal.netty;

import io.netty.channel.ChannelHandlerContext;

import com.notnoop.apns.DeliveryResult;

/**
 * Listener interface for delivery results received from Apple.
 * 
 * @author flozano
 * 
 */
public interface DeliveryResultListener {

    void onDeliveryResult(ChannelHandlerContext ctx, DeliveryResult msg);

}
