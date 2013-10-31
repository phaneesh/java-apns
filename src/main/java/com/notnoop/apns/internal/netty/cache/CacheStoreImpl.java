package com.notnoop.apns.internal.netty.cache;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryResult;

public class CacheStoreImpl implements CacheStore {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(CacheStoreImpl.class);
    private final Queue<ApnsNotification> cachedNotifications,
            notificationsBuffer;
    private final boolean autoAdjustCacheLength;
    private int cacheLength;

    public CacheStoreImpl(int cacheLength, boolean autoAdjustCacheLength) {
        this.cachedNotifications = new ConcurrentLinkedQueue<ApnsNotification>();
        this.notificationsBuffer = new ConcurrentLinkedQueue<ApnsNotification>();
        this.cacheLength = cacheLength;
        this.autoAdjustCacheLength = autoAdjustCacheLength;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.notnoop.apns.internal.netty.cache.CacheStore#resizeCacheIfNeeded(int)
     */
    @Override
    public Integer resizeCacheIfNeeded(int n) {
        if (autoAdjustCacheLength) {
            cacheLength = cacheLength + (n / 2);
            LOGGER.info("Adjusting APNS cache length to {}", cacheLength);
            return cacheLength;
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.notnoop.apns.internal.netty.cache.CacheStore#addAll(java.util.Collection
     * )
     */
    @Override
    public void addAll(Collection<ApnsNotification> apnsNotifications) {
        cachedNotifications.addAll(apnsNotifications);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.notnoop.apns.internal.netty.cache.CacheStore#add(com.notnoop.apns
     * .ApnsNotification)
     */
    @Override
    public void add(ApnsNotification notification) {
        cachedNotifications.add(notification);
        while (cachedNotifications.size() > cacheLength) {
            cachedNotifications.poll();
            LOGGER.debug("Removing notification from cache {}", notification);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.notnoop.apns.internal.netty.cache.CacheStore#moveCacheToBuffer()
     */
    @Override
    public int moveCacheToBuffer() {
        int resendSize = 0;
        ApnsNotification cachedNotification = null;
        while ((cachedNotification = cachedNotifications.poll()) != null) {
            resendSize++;
            notificationsBuffer.add(cachedNotification);
        }
        return resendSize;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.notnoop.apns.internal.netty.cache.CacheStore#drain(com.notnoop.apns
     * .internal.netty.cache.CacheStoreImpl.Drainer)
     */
    @Override
    public void drain(Drainer drainer) {
        ApnsNotification notification = null;
        while ((notification = notificationsBuffer.poll()) != null) {
            LOGGER.debug("Resending notification {} from buffer",
                    notification.getIdentifier());
            drainer.process(notification);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.notnoop.apns.internal.netty.cache.CacheStore#removeAllBefore(com.
     * notnoop.apns.DeliveryResult, java.util.Collection)
     */
    @Override
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

    /*
     * (non-Javadoc)
     * 
     * @see com.notnoop.apns.internal.netty.cache.CacheStore#setCacheLength(int)
     */
    @Override
    public void setCacheLength(int cacheLength) {
        this.cacheLength = cacheLength;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.notnoop.apns.internal.netty.cache.CacheStore#getCacheLength()
     */
    @Override
    public int getCacheLength() {
        return cacheLength;
    }

}
