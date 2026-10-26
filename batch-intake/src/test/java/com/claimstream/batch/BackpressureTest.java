package com.claimstream.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pipelines must never run more lines at once than they promise, and must surface a failing line. */
class BackpressureTest {

    @TempDir
    Path dir;

    @Test
    void virtualThreadsNeverExceedTheInFlightLimit() throws Exception {
        ConcurrencyProbe probe = runWithProbe(new VirtualThreadPipeline(20));

        assertThat(probe.handled.sum()).isEqualTo(400);
        assertThat(probe.peak.get()).isLessThanOrEqualTo(20);
    }

    @Test
    void threadPoolNeverRunsMoreLinesThanWorkers() throws Exception {
        ConcurrencyProbe probe = runWithProbe(new ThreadPoolPipeline(4, 8));

        assertThat(probe.handled.sum()).isEqualTo(400);
        assertThat(probe.peak.get()).isLessThanOrEqualTo(4);
    }

    @Test
    void aFailingLineIsReportedNotSwallowed() throws Exception {
        Path file = dir.resolve("batch.txt");
        BatchFileGenerator.write(file, 100);
        LineHandler exploding = line -> {
            if (line.startsWith("CLM-100050|")) {
                throw new IllegalStateException("boom");
            }
        };

        assertThatThrownBy(() -> new ThreadPoolPipeline(4, 8).process(file, exploding))
                .hasRootCauseMessage("boom");
        assertThatThrownBy(() -> new VirtualThreadPipeline(10).process(file, exploding))
                .hasRootCauseMessage("boom");
    }

    private ConcurrencyProbe runWithProbe(IntakePipeline pipeline) throws Exception {
        Path file = dir.resolve("probe.txt");
        BatchFileGenerator.write(file, 400);
        ConcurrencyProbe probe = new ConcurrencyProbe();
        pipeline.process(file, probe);
        return probe;
    }

    /** Records the most lines ever handled at the same moment. */
    private static final class ConcurrencyProbe implements LineHandler {

        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();
        private final LongAdder handled = new LongAdder();

        @Override
        public void handle(String line) {
            int now = running.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            running.decrementAndGet();
            handled.increment();
        }
    }
}
