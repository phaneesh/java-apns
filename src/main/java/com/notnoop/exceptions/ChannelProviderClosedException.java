package com.notnoop.exceptions;

public class ChannelProviderClosedException extends ApnsException {

    private static final long serialVersionUID = 1L;

    public ChannelProviderClosedException() {
        super("The channel provider has been closed");
    }
}
