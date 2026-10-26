package com.claimstream.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One virtual thread per claim line. Virtual threads are cheap (a few hundred bytes, scheduled by the JVM, not the
 * OS), so we don't pool them. When one blocks on I/O, the JVM parks it and runs another on the same carrier thread.
 *
 * <p>Backpressure: a {@link Semaphore} caps how many lines are in flight. Without it the reader would start 100k
 * threads at once, and a real downstream service would be flooded.
 */
public final class VirtualThreadPipeline implements IntakePipeline {

    private final int maxInFlight;

    public VirtualThreadPipeline(int maxInFlight) {
        this.maxInFlight = maxInFlight;
    }

    @Override
    public String name() {
        return "Virtual threads (" + maxInFlight + " in flight)";
    }

    @Override
    public void process(Path batchFile, LineHandler handler) throws IOException, InterruptedException {
        Semaphore permits = new Semaphore(maxInFlight);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        // close() at the end of try-with-resources waits for every submitted task to finish.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             BufferedReader in = Files.newBufferedReader(batchFile)) {
            String line;
            while ((line = in.readLine()) != null && firstFailure.get() == null) {
                if (!IntakePipeline.isClaimLine(line)) {
                    continue;
                }
                permits.acquire(); // blocks while maxInFlight lines are still being handled
                String claimLine = line;
                executor.submit(() -> {
                    try {
                        handler.handle(claimLine);
                    } catch (RuntimeException e) {
                        firstFailure.compareAndSet(null, e);
                    } finally {
                        permits.release();
                    }
                });
            }
        }
        if (firstFailure.get() != null) {
            throw new IllegalStateException("A claim line failed", firstFailure.get());
        }
    }
}
