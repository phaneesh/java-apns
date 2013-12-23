package com.notnoop.exceptions;

/**
 * Exception to be thrown when trying to send a push message through a
 * connection in an ApnsService when the service has already been closed. It
 * contains the deviceToken failed in the push operation.
 * 
 * @author Jorge
 * 
 */
public class ApnsServiceStoppedException extends ApnsException {

    private static final long serialVersionUID = 1L;

    private final String failedDeviceToken;

    public ApnsServiceStoppedException(String failedDeviceToken) {
        this.failedDeviceToken = failedDeviceToken;
    }

    public String getFailedDeviceToken() {
        return failedDeviceToken;
    }

}
