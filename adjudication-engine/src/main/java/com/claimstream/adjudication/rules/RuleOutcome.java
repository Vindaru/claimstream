package com.claimstream.adjudication.rules;

import java.util.Objects;

/**
 * What a rule decided. CONTINUE passes the claim to the next rule; DENY and PEND stop the chain.
 *
 * @param reasonCode short machine-readable code, e.g. DUPLICATE_CLAIM (maps to a CARC on the 835 later)
 */
public record RuleOutcome(Verdict verdict, String reasonCode, String message) {

    private static final RuleOutcome CONTINUE = new RuleOutcome(Verdict.CONTINUE, "", "");

    public enum Verdict { CONTINUE, DENY, PEND }

    public RuleOutcome {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(message, "message");
    }

    public static RuleOutcome proceed() {
        return CONTINUE;
    }

    public static RuleOutcome deny(String reasonCode, String message) {
        return new RuleOutcome(Verdict.DENY, reasonCode, message);
    }

    public static RuleOutcome pend(String reasonCode, String message) {
        return new RuleOutcome(Verdict.PEND, reasonCode, message);
    }

    public boolean stopsTheChain() {
        return verdict != Verdict.CONTINUE;
    }
}
