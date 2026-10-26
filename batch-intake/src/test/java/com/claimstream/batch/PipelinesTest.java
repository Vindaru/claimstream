package com.claimstream.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimstream.adjudication.rules.EligibilityRule;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every pipeline must reach exactly the same counts: threading may change speed, never the answer. */
class PipelinesTest {

    private static final int CLAIMS = 5_000;

    @TempDir
    static Path dir;

    static Stream<IntakePipeline> pipelines() {
        return Stream.of(new SingleThreadedPipeline(), new ThreadPoolPipeline(4, 10),
                new ThreadPoolPipeline(32, 100), new VirtualThreadPipeline(500));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pipelines")
    void everyPipelineCountsEveryClaimExactlyOnce(IntakePipeline pipeline) throws Exception {
        Path file = dir.resolve("batch-" + pipeline.name().hashCode() + ".txt");
        BatchFileGenerator.write(file, CLAIMS);
        ClaimIntake intake = new ClaimIntake(new EligibilityRule(Roster.standard()));

        pipeline.process(file, intake);

        assertThat(intake.report().snapshot()).isEqualTo(Map.of(
                BatchReport.ACCEPTED, BatchFileGenerator.expected(BatchReport.ACCEPTED, CLAIMS),
                "DENIED:COVERAGE_TERMINATED", BatchFileGenerator.expected("DENIED:COVERAGE_TERMINATED", CLAIMS),
                "DENIED:MEMBER_NOT_FOUND", BatchFileGenerator.expected("DENIED:MEMBER_NOT_FOUND", CLAIMS),
                BatchReport.MALFORMED, BatchFileGenerator.expected(BatchReport.MALFORMED, CLAIMS)));
        assertThat(intake.report().total()).isEqualTo(CLAIMS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pipelines")
    void slowLookupsStillGiveTheSameAnswer(IntakePipeline pipeline) throws Exception {
        Path file = dir.resolve("slow-" + pipeline.name().hashCode() + ".txt");
        BatchFileGenerator.write(file, 300);
        ClaimIntake intake = new ClaimIntake(
                new EligibilityRule(new SlowMemberDirectory(Roster.standard(), Duration.ofNanos(50_000))));

        pipeline.process(file, intake);

        assertThat(intake.report().count(BatchReport.ACCEPTED))
                .isEqualTo(BatchFileGenerator.expected(BatchReport.ACCEPTED, 300));
        assertThat(intake.report().total()).isEqualTo(300);
    }
}
