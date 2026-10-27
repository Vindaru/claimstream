package com.claimstream.intake.claims;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Why "check, then insert" is not enough, made deterministic: a barrier holds both threads after their SELECT
 * until both have seen "no claim yet", then both INSERT. Without a unique constraint, two rows appear.
 */
@SpringBootTest
class RaceConditionTest extends PostgresAndRedis {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void checkThenInsertWithoutAConstraintStoresTheClaimTwice() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS naive_claims (claim_id VARCHAR(32))"); // no PRIMARY KEY

        runTwoRacingThreads("naive_claims");

        assertThat(count("naive_claims")).isEqualTo(2); // the bug
    }

    @Test
    void theSameRaceAgainstAPrimaryKeyStoresItOnce() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS guarded_claims (claim_id VARCHAR(32) PRIMARY KEY)");

        int failures = runTwoRacingThreads("guarded_claims");

        assertThat(count("guarded_claims")).isEqualTo(1);
        assertThat(failures).isEqualTo(1); // the loser got a DuplicateKeyException
    }

    private int runTwoRacingThreads(String table) throws Exception {
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        Runnable checkThenInsert = () -> {
            Long existing = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE claim_id = 'CLM-5002'",
                    Long.class);
            await(bothChecked); // both threads have now seen 0 rows
            if (existing == 0) {
                jdbc.update("INSERT INTO " + table + " (claim_id) VALUES ('CLM-5002')");
            }
        };
        int failures = 0;
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Future<?> a = threads.submit(checkThenInsert);
            Future<?> b = threads.submit(checkThenInsert);
            for (Future<?> f : new Future<?>[] {a, b}) {
                try {
                    f.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(DuplicateKeyException.class);
                    failures++;
                }
            }
        }
        return failures;
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
