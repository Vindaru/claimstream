package com.claimstream.adjudicationservice;

import com.claimstream.adjudication.AdjudicationEngine;
import com.claimstream.adjudication.AdjudicationResult;
import com.claimstream.adjudication.benefits.BenefitBreakdown;
import com.claimstream.domain.Claim;
import com.claimstream.domain.Money;
import com.claimstream.domain.events.ClaimAdjudicated;
import com.claimstream.domain.events.ClaimReceived;
import com.claimstream.domain.events.Topics;
import com.claimstream.outbox.OutboxWriter;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides one claim, exactly once, however many times its event is delivered.
 *
 * <p>All in one database transaction: check "seen it?", run the rules, save the verdict, write the
 * ClaimAdjudicated event to the outbox. A crash anywhere rolls back everything, Kafka redelivers, and we
 * start clean. A redelivery after a successful commit finds the verdict and does nothing.
 */
@Service
public class ClaimAdjudicator {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimAdjudicator.class);

    /** What happened to one delivery: a new decision, or a duplicate we skipped. */
    public enum Outcome { DECIDED, DUPLICATE_SKIPPED }

    private final AdjudicationEngine engine;
    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;

    public ClaimAdjudicator(AdjudicationEngine engine, JdbcTemplate jdbc, OutboxWriter outbox) {
        this.engine = engine;
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional
    public Outcome handle(ClaimReceived event) {
        // Idempotent consumer: at-least-once delivery means this exact event may arrive twice.
        Integer seen = jdbc.queryForObject("SELECT count(*) FROM adjudications WHERE claim_id = ?",
                Integer.class, event.claimId());
        if (seen != null && seen > 0) {
            LOG.info("claim={} already adjudicated, skipping redelivery", event.claimId());
            return Outcome.DUPLICATE_SKIPPED;
        }

        Claim claim = event.toClaim();
        AdjudicationResult result = engine.adjudicate(claim);
        BenefitBreakdown b = result.breakdown().orElse(null);
        ClaimAdjudicated verdict = new ClaimAdjudicated(event.claimId(), event.memberId(), result.status().name(),
                result.reasonCode(), result.message(), amount(b == null ? null : b.allowed()),
                amount(b == null ? null : b.planPays()), amount(b == null ? null : b.memberPays()));

        // The primary key is the last line of defence if two threads ever race on the same claim.
        jdbc.update("INSERT INTO adjudications (claim_id, member_id, status, reason_code, message, allowed,"
                        + " plan_pays, member_pays) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                verdict.claimId(), verdict.memberId(), verdict.status(), verdict.reasonCode(), verdict.message(),
                verdict.allowed(), verdict.planPays(), verdict.memberPays());
        outbox.append(Topics.CLAIMS_ADJUDICATED, event.memberId(), verdict);
        LOG.info("claim={} status={} reason={}", verdict.claimId(), verdict.status(), verdict.reasonCode());
        return Outcome.DECIDED;
    }

    private static BigDecimal amount(Money money) {
        return money == null ? null : money.amount();
    }
}
