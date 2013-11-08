package com.notnoop.apns.internal.netty.util.concurrency;

import java.util.concurrent.Callable;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PrioritizedExecutorService extends ThreadPoolExecutor {
    
    public PrioritizedExecutorService(int corePoolSize, int maximumPoolSize,
            TimeUnit unit) {
        super(corePoolSize, maximumPoolSize, 0, TimeUnit.NANOSECONDS,
                new PriorityBlockingQueue<Runnable>());
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new PriorityRunnableFuture<>(super.newTaskFor(callable),
                Integer.MAX_VALUE);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        RunnableFuture<T> rf = super.newTaskFor(runnable, value);
        final int priority;
        if (runnable instanceof PriorityRunnable) {
            priority = ((PriorityRunnable) runnable).getPriority();
        } else {
            priority = Integer.MAX_VALUE;
        }
        return new PriorityRunnableFuture<>(rf, priority);
    }
}
