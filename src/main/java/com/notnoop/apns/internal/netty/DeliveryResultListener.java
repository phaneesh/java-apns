package com.notnoop.apns.internal.netty;

import com.notnoop.apns.DeliveryResult;

/**
 * Listener interface for delivery results received from Apple.
 * 
 * @author flozano
 * 
 */
public interface DeliveryResultListener {

    void onDeliveryResult(DeliveryResult msg);

}
