package com.claimstream.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Shows a race condition: several threads doing {@code count++} on a plain field lose updates, because {@code ++}
 * is three steps (read, add, write) and two threads can read the same old value.
 */
public final class RaceConditionDemo {

    private int unsafeCount;
    private final LongAdder safeCount = new LongAdder();

    public record Result(long expected, long unsafe, long safe) {
    }

    public static Result run(int threads, int incrementsPerThread) throws InterruptedException {
        RaceConditionDemo demo = new RaceConditionDemo();
        List<Thread> started = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            started.add(Thread.ofPlatform().start(() -> {
                for (int i = 0; i < incrementsPerThread; i++) {
                    demo.unsafeCount++;
                    demo.safeCount.increment();
                }
            }));
        }
        for (Thread thread : started) {
            thread.join();
        }
        return new Result((long) threads * incrementsPerThread, demo.unsafeCount, demo.safeCount.sum());
    }
}
