package com.notnoop.apns.internal.netty.cache;

import java.util.Collection;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.DeliveryResult;

public interface CacheStore {
    /**
     * If this cache requires resize, resize it. TODO make this operation less
     * 'weird'
     * 
     * @param n
     * @return
     */
    Integer resizeCacheIfNeeded(int n);

    /**
     * Add all items in the collection to the cache of notifications
     * 
     * @param apnsNotifications
     */
    void addAll(Collection<ApnsNotification> apnsNotifications);

    /**
     * Add a single notification to the cache.
     * 
     * @param notification
     */
    void add(ApnsNotification notification);

    /**
     * Move existing items in the cache to the buffer.
     * 
     * @return The number of items moved.
     */
    int moveCacheToBuffer();

    /**
     * Interface to process buffer items.
     * 
     * @author flozano
     * 
     */
    public static interface Drainer {
        public void process(ApnsNotification notification);
    }

    /**
     * Drain the buffer and invoke drainer.process() for each item in the
     * buffer.
     * 
     * @param drainer
     */
    void drain(Drainer drainer);

    /**
     * Remove all items before the referred to this delivery result, and add
     * them to removed collection.
     * 
     * @param deliveryResult
     * @param removed
     * @return The found item referred by the provided delivery result, if it
     *         was found.
     */
    ApnsNotification removeAllBefore(DeliveryResult deliveryResult,
            Collection<ApnsNotification> removed);

    void setCacheLength(int cacheLength);

    int getCacheLength();

}