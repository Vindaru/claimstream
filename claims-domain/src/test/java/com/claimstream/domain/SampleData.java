package com.claimstream.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** The running example cast, built once from the domain model. Reused by every milestone's tests. */
public final class SampleData {

    public static final LocalDate DATE_OF_SERVICE = LocalDate.of(2026, 10, 20);

    public static final Plan GOLD_PPO = new Plan("GOLD-PPO", Money.of("500"), new BigDecimal("0.80"));

    public static final Member MARIA = new Member(new MemberId("M-1001"), "Maria Lopez", GOLD_PPO,
            CoveragePeriod.openFrom(LocalDate.of(2026, 1, 1)), Money.of("350"));

    /** Coverage ended last month, so CLM-5003 must be denied. */
    public static final Member LAPSED_MEMBER = new Member(new MemberId("M-2002"), "James Chen", GOLD_PPO,
            new CoveragePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30)), Money.ZERO);

    public static final Provider DR_PATEL =
            new Provider(new Npi("1234567890"), "Dr. Patel Family Clinic", true);

    public static final Provider QUICK_CARE =
            new Provider(new Npi("9876543210"), "Quick Care Clinic", true);

    public static final ClaimLine OFFICE_VISIT = new ClaimLine(1, "99213", Money.of("150"));

    public static final Claim CLM_5001 =
            officeVisit("CLM-5001", MARIA.id(), DR_PATEL.npi(), DATE_OF_SERVICE);

    /** Exact duplicate of CLM-5001: same member, provider, service and date. */
    public static final Claim CLM_5002 =
            officeVisit("CLM-5002", MARIA.id(), DR_PATEL.npi(), DATE_OF_SERVICE);

    public static final Claim CLM_5003 =
            officeVisit("CLM-5003", LAPSED_MEMBER.id(), DR_PATEL.npi(), DATE_OF_SERVICE);

    /** The 40th office visit Quick Care billed for one day. */
    public static final Claim CLM_5004 =
            officeVisit("CLM-5004", new MemberId("M-3040"), QUICK_CARE.npi(), DATE_OF_SERVICE);

    /** A normal claim that arrives while the Eligibility Service is down (M6). */
    public static final Claim CLM_5005 =
            officeVisit("CLM-5005", MARIA.id(), DR_PATEL.npi(), DATE_OF_SERVICE.plusDays(1));

    private SampleData() {
    }

    /** The 39 visits Quick Care billed before CLM-5004 on the same day. */
    public static List<Claim> quickCareEarlierVisits() {
        return IntStream.rangeClosed(1, 39)
                .mapToObj(i -> officeVisit("CLM-%d".formatted(7000 + i),
                        new MemberId("M-%d".formatted(3000 + i)), QUICK_CARE.npi(), DATE_OF_SERVICE))
                .toList();
    }

    /** A covered member for the Quick Care visits; deductible already met so only the visit limit matters. */
    public static Member quickCarePatient(MemberId id) {
        return new Member(id, "Patient " + id, GOLD_PPO, CoveragePeriod.openFrom(LocalDate.of(2026, 1, 1)),
                GOLD_PPO.deductible());
    }

    private static Claim officeVisit(String claimId, MemberId member, Npi npi, LocalDate date) {
        return new Claim(new ClaimId(claimId), member, npi, date, List.of(OFFICE_VISIT));
    }
}
