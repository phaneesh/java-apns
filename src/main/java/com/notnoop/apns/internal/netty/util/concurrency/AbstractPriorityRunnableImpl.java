package com.notnoop.apns.internal.netty.util.concurrency;

public abstract class AbstractPriorityRunnableImpl implements PriorityRunnable {

    private final int priority;

    public AbstractPriorityRunnableImpl(int priority) {
        this.priority = priority;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public abstract void run();

    @Override
    public int compareTo(PriorityRunnable o) {
        return Integer.compare(priority, o.getPriority());
    }
}