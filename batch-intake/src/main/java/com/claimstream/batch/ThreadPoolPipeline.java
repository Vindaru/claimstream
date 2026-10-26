package com.claimstream.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Producer-consumer. The calling thread reads the file and {@code put}s lines on a bounded queue; a fixed pool of
 * workers {@code take}s and handles them.
 *
 * <p>Backpressure: when the queue is full, {@code put} blocks, so a fast reader can never flood memory with
 * 100k waiting lines. Shutdown: one "poison pill" per worker tells it the file is finished.
 */
public final class ThreadPoolPipeline implements IntakePipeline {

    // Compared by identity (==), so a claim line with the same text can never be mistaken for it.
    private static final String POISON_PILL = new String("END-OF-BATCH");

    private final int workers;
    private final int queueCapacity;

    public ThreadPoolPipeline(int workers, int queueCapacity) {
        this.workers = workers;
        this.queueCapacity = queueCapacity;
    }

    @Override
    public String name() {
        return "Thread pool (" + workers + " threads)";
    }

    @Override
    public void process(Path batchFile, LineHandler handler) throws IOException, InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(queueCapacity);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            for (int i = 0; i < workers; i++) {
                pool.execute(() -> consume(queue, handler, firstFailure));
            }
            produce(batchFile, queue);
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.HOURS);
        } finally {
            pool.shutdownNow();
        }
        if (firstFailure.get() != null) {
            throw new IllegalStateException("A claim line failed", firstFailure.get());
        }
    }

    private void produce(Path batchFile, BlockingQueue<String> queue) throws IOException, InterruptedException {
        try (BufferedReader in = Files.newBufferedReader(batchFile)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (IntakePipeline.isClaimLine(line)) {
                    queue.put(line); // blocks while the queue is full: backpressure
                }
            }
        } finally {
            for (int i = 0; i < workers; i++) {
                queue.put(POISON_PILL);
            }
        }
    }

    /** Keeps draining after a failure, so the producer can never block forever on a full queue. */
    @SuppressWarnings("StringEquality")
    private static void consume(BlockingQueue<String> queue, LineHandler handler, AtomicReference<Throwable> failure) {
        try {
            while (true) {
                String line = queue.take(); // blocks while the queue is empty
                if (line == POISON_PILL) {
                    return;
                }
                try {
                    handler.handle(line);
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
