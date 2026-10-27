package com.claimstream.batch;

import com.claimstream.adjudication.rules.EligibilityRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Generates a batch of claims and runs it through every pipeline, twice: once I/O-bound (each member lookup waits,
 * like a network call) and once CPU-bound (no wait). Prints a timing table for each.
 *
 * <p>Run: {@code java -jar batch-intake/target/batch-intake.jar [claims] [lookupMicros]}, defaults 100000 and 100.
 */
public final class IntakeBenchmark {

    private IntakeBenchmark() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int claims = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        Duration lookup = Duration.ofNanos(1_000L * (args.length > 1 ? Integer.parseInt(args[1]) : 100));
        int cores = Runtime.getRuntime().availableProcessors();
        List<IntakePipeline> pipelines = List.of(
                new SingleThreadedPipeline(),
                new ThreadPoolPipeline(cores, 1_000),
                new ThreadPoolPipeline(64, 1_000),
                new VirtualThreadPipeline(1_000));

        Path file = Files.createTempFile("claims-batch-", ".txt");
        try {
            BatchFileGenerator.write(file, claims);
            System.out.printf("Batch file: %,d claims, %d CPU cores%n", claims, cores);
            warmUp(pipelines, file);
            Map<String, Long> counts = table(pipelines, file, lookup,
                    "I/O-bound: each member lookup waits " + lookup.toNanos() / 1_000 + " us");
            table(pipelines, file, Duration.ZERO, "CPU-bound: member lookup is instant");
            System.out.println("\nOutcomes (identical for every pipeline): " + counts);
        } finally {
            Files.deleteIfExists(file);
        }

        RaceConditionDemo.Result race = RaceConditionDemo.run(cores, 1_000_000);
        System.out.printf("%nRace condition, %d threads x 1,000,000 increments: expected %,d, count++ got %,d"
                + " (lost %,d), LongAdder got %,d%n", cores, race.expected(), race.unsafe(),
                race.expected() - race.unsafe(), race.safe());
    }

    private static Map<String, Long> table(List<IntakePipeline> pipelines, Path file, Duration lookup, String title)
            throws IOException, InterruptedException {
        System.out.printf("%n%s%n%-34s %9s %12s %9s%n", title, "Pipeline", "Time", "Claims/sec", "Speed-up");
        double baseline = 0;
        Map<String, Long> firstCounts = null;
        for (IntakePipeline pipeline : pipelines) {
            ClaimIntake intake = intake(lookup);
            long start = System.nanoTime();
            pipeline.process(file, intake);
            double seconds = (System.nanoTime() - start) / 1e9;
            baseline = baseline == 0 ? seconds : baseline;
            Map<String, Long> counts = intake.report().snapshot();
            if (firstCounts == null) {
                firstCounts = counts;
            } else if (!counts.equals(firstCounts)) {
                throw new IllegalStateException(pipeline.name() + " counted " + counts + ", expected " + firstCounts);
            }
            System.out.printf("%-34s %8.2fs %,12.0f %8.1fx%n", pipeline.name(), seconds,
                    intake.report().total() / seconds, baseline / seconds);
        }
        return firstCounts;
    }

    /** Lets the JIT compile the hot code first, so the first pipeline measured isn't unfairly slow. */
    private static void warmUp(List<IntakePipeline> pipelines, Path file) throws IOException, InterruptedException {
        for (IntakePipeline pipeline : pipelines) {
            pipeline.process(file, intake(Duration.ZERO));
        }
    }

    private static ClaimIntake intake(Duration lookup) {
        return new ClaimIntake(new EligibilityRule(new SlowMemberDirectory(Roster.standard(), lookup)));
    }
}
