package com.notnoop.apns.internal.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.ApnsDelegate;
import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryResult;
import com.notnoop.apns.internal.ApnsConnection;
import com.notnoop.apns.internal.Utilities;
import com.notnoop.apns.internal.netty.ChannelProvider.ChannelHandlersProvider;
import com.notnoop.exceptions.ApnsDeliveryErrorException;
import com.notnoop.exceptions.NetworkIOException;

public class NettyApnsConnectionImpl implements ApnsConnection {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(NettyApnsConnectionImpl.class);
	private final Queue<ApnsNotification> cachedNotifications,
			notificationsBuffer;
	private final ApnsDelegate delegate;
	private final ChannelProvider channelProvider;

	private final boolean autoAdjustCacheLength;
	private int cacheLength;

	public NettyApnsConnectionImpl(ChannelProvider channelProvider,
			ApnsDelegate delegate, int cacheLength,
			boolean autoAdjustCacheLength) {
		this.cachedNotifications = new ConcurrentLinkedQueue<ApnsNotification>();
		this.notificationsBuffer = new ConcurrentLinkedQueue<ApnsNotification>();
		this.delegate = delegate;
		this.channelProvider = channelProvider;
		this.cacheLength = cacheLength;
		this.autoAdjustCacheLength = autoAdjustCacheLength;
	}

	// This was done in the constructor, but it was impossible to spy the
	// reference to NettyApnsConnectionImpl.this. This is code smell and needs
	// to be addressed... fur to the shake of testability, for now I'll keep it
	// this way...
	public void init() {
		channelProvider
				.setChannelHandlersProvider(new ChannelHandlersProvider() {
					@Override
					public List<ChannelHandler> getChannelHandlers() {
						return Arrays.<ChannelHandler> asList(
								new ApnsNotificationEncoder(),
								new ApnsResultDecoder(), new ApnsHandler(
										NettyApnsConnectionImpl.this));
					}
				});

		channelProvider.init();
	}

	@Override
	public synchronized void close() throws IOException {
		channelProvider.close();
	}

	private void cacheNotification(ApnsNotification notification) {
		cachedNotifications.add(notification);
		while (cachedNotifications.size() > cacheLength) {
			cachedNotifications.poll();
			LOGGER.debug("Removing notification from cache " + notification);
		}
	}

	@Override
	public synchronized void sendMessage(ApnsNotification m)
			throws NetworkIOException {
		sendMessage(m, false);
	}

	protected synchronized void sendMessage(ApnsNotification m,
			boolean fromBuffer) {
		Channel channel = channelProvider.getChannel();

		while (true) {
			try {
				cacheNotification(m);
				channel.writeAndFlush(m);

				delegate.messageSent(m, fromBuffer);
				LOGGER.debug("Message \"{}\" sent", m);
				drainBuffer();
				break;
			} catch (Exception e) {
				delegate.messageSendFailed(m, e);
				Utilities.wrapAndThrowAsRuntimeException(e);
			}
		}
	}

	private void drainBuffer() {
		ApnsNotification notification = null;
		if ((notification = notificationsBuffer.poll()) != null) {
			LOGGER.debug("Resending notification {} from buffer",
					notification.getIdentifier());
			sendMessage(notification, true);
		}
	}

	public void onMessageReceived(ChannelHandlerContext ctx, DeliveryResult msg) {
		try {
			Queue<ApnsNotification> tempCache = new LinkedList<ApnsNotification>();
			ApnsNotification notification = null;
			boolean foundNotification = false;

			while ((notification = cachedNotifications.poll()) != null) {
				if (notification.getIdentifier() == msg.getId()) {
					foundNotification = true;
					break;
				}
				tempCache.add(notification);
			}

			if (foundNotification) {
				delegate.messageSendFailed(notification,
						new ApnsDeliveryErrorException(msg.getError()));
			} else {
				cachedNotifications.addAll(tempCache);
				LOGGER.warn("Received error for message "
						+ "that wasn't in the cache...");
				if (autoAdjustCacheLength) {
					cacheLength = cacheLength + (tempCache.size() / 2);
					delegate.cacheLengthExceeded(cacheLength);
				}
				delegate.messageSendFailed(null,
						new ApnsDeliveryErrorException(msg.getError()));
			}

			int resendSize = 0;
			ApnsNotification cachedNotification = null;
			while ((cachedNotification = cachedNotifications.poll()) != null) {
				resendSize++;
				notificationsBuffer.add(cachedNotification);
			}
			delegate.notificationsResent(resendSize);
			delegate.connectionClosed(msg.getError(), msg.getId());
		} finally {
			try {
				close();
				drainBuffer();

			} catch (IOException e) {
				LOGGER.error("I/O Exception while closing", e);
			}
		}
	}

	@Override
	public void testConnection() throws NetworkIOException {
		// TODO Auto-generated method stub

	}

	@Override
	public ApnsConnection copy() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setCacheLength(int cacheLength) {
		this.cacheLength = cacheLength;
	}

	@Override
	public int getCacheLength() {
		return cacheLength;
	}

}
