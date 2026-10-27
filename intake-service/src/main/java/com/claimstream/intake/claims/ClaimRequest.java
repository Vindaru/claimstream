package com.claimstream.intake.claims;

import com.claimstream.domain.Claim;
import com.claimstream.domain.ClaimId;
import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.MemberId;
import com.claimstream.domain.Money;
import com.claimstream.domain.Npi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** JSON body of {@code POST /api/v1/claims}. Validated by building the M1 domain model from it. */
public record ClaimRequest(String claimId, String memberId, String providerNpi, LocalDate dateOfService,
                           List<Line> lines) {

    public record Line(int lineNumber, String cptCode, BigDecimal billed) {
    }

    /** @throws InvalidClaimException when any field breaks a domain rule (bad NPI, empty lines, $0 line...) */
    public Claim toClaim() {
        try {
            return new Claim(new ClaimId(claimId), new MemberId(memberId), new Npi(providerNpi), dateOfService,
                    lines.stream()
                            .map(l -> new ClaimLine(l.lineNumber(), l.cptCode(), Money.of(l.billed().toPlainString())))
                            .toList());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidClaimException(e.getMessage() == null ? "Missing field" : e.getMessage());
        }
    }
}
