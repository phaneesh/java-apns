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
    public final void run() {
        final String orgName = Thread.currentThread().getName();
        Thread.currentThread().setName(
                orgName + " - Executing task with priority #" + priority);
        try {
            priorityRun();
        } finally {
            Thread.currentThread().setName(orgName);
        }
    }

    protected abstract void priorityRun();

    @Override
    public int compareTo(PriorityRunnable o) {
        return Integer.compare(priority, o.getPriority());
    }

}