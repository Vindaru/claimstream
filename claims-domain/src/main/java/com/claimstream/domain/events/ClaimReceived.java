package com.claimstream.domain.events;

import com.claimstream.domain.Claim;
import com.claimstream.domain.ClaimId;
import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.MemberId;
import com.claimstream.domain.Money;
import com.claimstream.domain.Npi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * "A claim was accepted at the front door." A fact in the past tense: it can't be rejected, only reacted to.
 *
 * <p>Plain strings and numbers (no domain objects) so any service, in any language, can read the JSON.
 */
public record ClaimReceived(String claimId, String memberId, String providerNpi, LocalDate dateOfService,
                            List<Line> lines) {

    public record Line(int lineNumber, String cptCode, BigDecimal billed) {
    }

    public static ClaimReceived from(Claim claim) {
        return new ClaimReceived(claim.id().value(), claim.memberId().value(), claim.providerNpi().value(),
                claim.dateOfService(), claim.lines().stream()
                        .map(l -> new Line(l.lineNumber(), l.cptCode(), l.billed().amount()))
                        .toList());
    }

    /** Rebuilds the domain claim; throws IllegalArgumentException if the event breaks a domain rule. */
    public Claim toClaim() {
        return new Claim(new ClaimId(claimId), new MemberId(memberId), new Npi(providerNpi), dateOfService,
                lines.stream()
                        .map(l -> new ClaimLine(l.lineNumber(), l.cptCode(), Money.of(l.billed().toPlainString())))
                        .toList());
    }
}
