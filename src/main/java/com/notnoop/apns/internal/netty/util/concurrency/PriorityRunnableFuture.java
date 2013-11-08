package com.notnoop.apns.internal.netty.util.concurrency;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PriorityRunnableFuture<T> implements RunnableFuture<T>,
        Comparable<PriorityRunnableFuture<T>> {
    private final RunnableFuture<T> inner;
    private final int priority;

    public PriorityRunnableFuture(RunnableFuture<T> inner, int priority) {
        this.inner = inner;
        this.priority = priority;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return inner.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return inner.isCancelled();
    }

    @Override
    public boolean isDone() {
        return inner.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return inner.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return inner.get(timeout, unit);
    }

    @Override
    public void run() {
        inner.run();
    }

    @Override
    public int compareTo(PriorityRunnableFuture<T> o) {
        return Integer.compare(priority, o.priority);
    }
}
