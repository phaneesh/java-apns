package com.notnoop.apns.internal.netty.util.concurrency;


public interface PriorityRunnable extends Runnable,
        Comparable<PriorityRunnable> {

    int getPriority();

}
