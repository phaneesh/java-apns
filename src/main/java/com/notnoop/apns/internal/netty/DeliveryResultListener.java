package com.notnoop.apns.internal.netty;

import com.notnoop.apns.DeliveryResult;

public interface DeliveryResultListener {

    void onDeliveryResult(DeliveryResult msg);

}
