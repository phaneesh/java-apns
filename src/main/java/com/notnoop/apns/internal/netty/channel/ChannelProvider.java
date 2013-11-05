package com.notnoop.apns.internal.netty.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

import java.io.IOException;
import java.util.List;

/**
 * This class separates the channel handling/reconnecting from the actual
 * app-level logic of APNS.
 * 
 * @author flozano
 * 
 */
public interface ChannelProvider {
    /**
     * Obtains the current channel, reconnecting if needed.
     * 
     * @return
     */
    // Channel getChannel();

    /**
     * closes current channel.
     * 
     * @throws IOException
     */
    void close() throws IOException;

    void runWithChannel(WithChannelAction action) throws Exception;

    public static interface WithChannelAction {

        void perform(Channel channel) throws Exception;

    }

    /**
     * Sets the provider for the list of handlers that will apply to the
     * channels used by this channel provider.
     * 
     * (Yes, too many ****Provider...)
     * 
     * @param channelHandlersProvider
     */
    void setChannelHandlersProvider(
            ChannelHandlersProvider channelHandlersProvider);

    /**
     * Initialize this channel provider. Like above - it's code smell, to be
     * removed if possible in the future.
     */
    void init();

    public static interface ChannelHandlersProvider {
        List<ChannelHandler> getChannelHandlers();
    }
}
