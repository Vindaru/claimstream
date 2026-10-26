package com.claimstream.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchReportTest {

    @Test
    void neverLosesACountWhenEightThreadsWriteAtOnce() throws InterruptedException {
        BatchReport report = new BatchReport();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            String outcome = t % 2 == 0 ? BatchReport.ACCEPTED : "DENIED:COVERAGE_TERMINATED";
            threads.add(Thread.ofPlatform().start(() -> {
                for (int i = 0; i < 10_000; i++) {
                    report.record(outcome);
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(report.count(BatchReport.ACCEPTED)).isEqualTo(40_000);
        assertThat(report.count("DENIED:COVERAGE_TERMINATED")).isEqualTo(40_000);
        assertThat(report.total()).isEqualTo(80_000);
    }

    @Test
    void longAdderKeepsEveryIncrementThatCountPlusPlusMayLose() throws InterruptedException {
        RaceConditionDemo.Result result = RaceConditionDemo.run(4, 100_000);

        assertThat(result.safe()).isEqualTo(result.expected());
        assertThat(result.unsafe()).isLessThanOrEqualTo(result.expected()); // usually less: lost updates
    }
}
