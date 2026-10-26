package com.claimstream.batch;

import com.claimstream.domain.Claim;
import com.claimstream.domain.ClaimId;
import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.MemberId;
import com.claimstream.domain.Money;
import com.claimstream.domain.Npi;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Parses one line of our simplified 837 batch format into a {@link Claim}:
 * {@code claimId|memberId|npi|dateOfService|cpt|billed},
 * e.g. {@code CLM-5001|M-1001|1234567890|2026-10-20|99213|150.00}.
 *
 * <p>Stateless, so one instance is safely shared by every thread.
 */
public final class ClaimLineParser {

    static final String HEADER = "#claimId|memberId|npi|dateOfService|cpt|billed";
    private static final int FIELDS = 6;

    public Claim parse(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length != FIELDS) {
            throw new MalformedLineException(line, null);
        }
        try {
            return new Claim(new ClaimId(f[0]), new MemberId(f[1]), new Npi(f[2]), LocalDate.parse(f[3]),
                    List.of(new ClaimLine(1, f[4], Money.of(f[5]))));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new MalformedLineException(line, e);
        }
    }

    public static String format(Claim claim) {
        ClaimLine line = claim.lines().get(0);
        return String.join("|", claim.id().value(), claim.memberId().value(), claim.providerNpi().value(),
                claim.dateOfService().toString(), line.cptCode(), line.billed().amount().toPlainString());
    }
}
