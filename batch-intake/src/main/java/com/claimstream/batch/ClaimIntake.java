package com.claimstream.batch;

import com.claimstream.adjudication.rules.EligibilityRule;
import com.claimstream.adjudication.rules.RuleOutcome;
import com.claimstream.domain.Claim;

/**
 * The unit of work for one line: parse it, check eligibility (reusing M2's {@link EligibilityRule}), tally the result.
 *
 * <p>Safe to call from many threads: the parser and rule hold no mutable state, the member directory is a
 * concurrent map, and the report is built for concurrent writes.
 */
public final class ClaimIntake implements LineHandler {

    private final ClaimLineParser parser = new ClaimLineParser();
    private final EligibilityRule eligibility;
    private final BatchReport report = new BatchReport();

    public ClaimIntake(EligibilityRule eligibility) {
        this.eligibility = eligibility;
    }

    @Override
    public void handle(String line) {
        Claim claim;
        try {
            claim = parser.parse(line);
        } catch (MalformedLineException e) {
            report.record(BatchReport.MALFORMED);
            return;
        }
        RuleOutcome outcome = eligibility.evaluate(claim);
        report.record(switch (outcome.verdict()) {
            case CONTINUE -> BatchReport.ACCEPTED;
            case DENY -> "DENIED:" + outcome.reasonCode();
            case PEND -> "PENDED:" + outcome.reasonCode();
        });
    }

    public BatchReport report() {
        return report;
    }
}
