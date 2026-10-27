package com.claimstream.batch;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a deterministic batch file, so every run (and every test) knows exactly what to expect.
 *
 * <p>Line 1 is CLM-5001 for Maria. Then, by line number {@code i}:
 * {@code i % 1000 == 9} is malformed, {@code i % 500 == 3} is an unknown member,
 * {@code i % 100 == 7} is James (coverage ended), and the rest are covered synthetic members.
 */
public final class BatchFileGenerator {

    private static final String[] CPT = {"99213", "99214", "36415", "80053"};
    private static final String[] BILLED = {"150.00", "210.00", "25.00", "95.00"};
    private static final String DOS = "2026-10-20";
    private static final String NPI = "1234567890";

    private BatchFileGenerator() {
    }

    public static void write(Path file, int claims) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file)) {
            out.write(ClaimLineParser.HEADER);
            out.newLine();
            for (int i = 1; i <= claims; i++) {
                out.write(line(i));
                out.newLine();
            }
        }
    }

    static String line(int i) {
        if (i == 1) {
            return "CLM-5001|M-1001|" + NPI + "|" + DOS + "|99213|150.00";
        }
        if (i % 1000 == 9) {
            return "CLM-" + (100_000 + i) + "|M-BROKEN|" + NPI;
        }
        String member;
        if (i % 500 == 3) {
            member = "M-999999";
        } else if (i % 100 == 7) {
            member = "M-2002";
        } else {
            member = "M-" + (Roster.FIRST_SYNTHETIC_ID + i % Roster.SYNTHETIC_MEMBERS);
        }
        int service = i % CPT.length;
        return "CLM-" + (100_000 + i) + "|" + member + "|" + NPI + "|" + DOS + "|" + CPT[service] + "|"
                + BILLED[service];
    }

    /** How many of each outcome a file of {@code claims} lines produces. */
    public static long expected(String outcome, int claims) {
        long malformed = 0;
        long unknown = 0;
        long lapsed = 0;
        for (int i = 2; i <= claims; i++) {
            if (i % 1000 == 9) {
                malformed++;
            } else if (i % 500 == 3) {
                unknown++;
            } else if (i % 100 == 7) {
                lapsed++;
            }
        }
        return switch (outcome) {
            case BatchReport.MALFORMED -> malformed;
            case "DENIED:MEMBER_NOT_FOUND" -> unknown;
            case "DENIED:COVERAGE_TERMINATED" -> lapsed;
            case BatchReport.ACCEPTED -> claims - malformed - unknown - lapsed;
            default -> 0;
        };
    }
}
