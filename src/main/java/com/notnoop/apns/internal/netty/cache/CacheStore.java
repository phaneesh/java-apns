package com.notnoop.apns.internal.netty.cache;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryResult;

public class CacheStore {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(CacheStore.class);
    private final Queue<ApnsNotification> cachedNotifications,
            notificationsBuffer;
    private final boolean autoAdjustCacheLength;
    private int cacheLength;

    public CacheStore(int cacheLength, boolean autoAdjustCacheLength) {
        this.cachedNotifications = new ConcurrentLinkedQueue<ApnsNotification>();
        this.notificationsBuffer = new ConcurrentLinkedQueue<ApnsNotification>();
        this.cacheLength = cacheLength;
        this.autoAdjustCacheLength = autoAdjustCacheLength;
    }

    public Integer resizeCacheIfNeeded(int n) {
        if (autoAdjustCacheLength) {
            cacheLength = cacheLength + (n / 2);
            LOGGER.info("Adjusting APNS cache length to {}", cacheLength);
            return cacheLength;
        }
        return null;
    }

    public void addAll(Collection<ApnsNotification> apnsNotifications) {
        cachedNotifications.addAll(apnsNotifications);
    }

    public void add(ApnsNotification notification) {
        cachedNotifications.add(notification);
        while (cachedNotifications.size() > cacheLength) {
            cachedNotifications.poll();
            LOGGER.debug("Removing notification from cache {}", notification);
        }
    }
    
    public int moveCacheToBuffer() {
        int resendSize = 0;
        ApnsNotification cachedNotification = null;
        while ((cachedNotification = cachedNotifications.poll()) != null) {
            resendSize++;
            notificationsBuffer.add(cachedNotification);
        }
        return resendSize;
    }

    public void drain(Drainer drainer) {
        ApnsNotification notification = null;
        while ((notification = notificationsBuffer.poll()) != null) {
            LOGGER.debug("Resending notification {} from buffer",
                    notification.getIdentifier());
            drainer.process(notification);
        }
    }

    public ApnsNotification removeAllBefore(DeliveryResult deliveryResult,
            Collection<ApnsNotification> removed) {
        ApnsNotification notification = null;
        while ((notification = cachedNotifications.poll()) != null) {
            if (notification.getIdentifier() == deliveryResult.getId()) {
                break;
            }
            removed.add(notification);
        }
        return notification;
    }

    public void setCacheLength(int cacheLength) {
        this.cacheLength = cacheLength;
    }

    public int getCacheLength() {
        return cacheLength;
    }

    public static interface Drainer {
        public void process(ApnsNotification notification);
    }
}
