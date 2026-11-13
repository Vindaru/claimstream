package com.claimstream.adjudicationservice;

import com.claimstream.adjudication.ClaimStatus;
import com.claimstream.adjudication.ports.ClaimHistory;
import com.claimstream.domain.Claim;
import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.Npi;
import java.sql.Date;
import java.time.LocalDate;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The engine's ClaimHistory port, backed by Postgres so every consumer thread and instance sees the same past. */
@Repository
public class JdbcClaimHistory implements ClaimHistory {

    private final JdbcTemplate jdbc;

    public JdbcClaimHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasPaidMatching(Claim claim) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM claim_history WHERE member_id = ? AND provider_npi = ?"
                        + " AND date_of_service = ? AND cpt_codes = ? AND status = 'PAID'", Long.class,
                claim.memberId().value(), claim.providerNpi().value(), Date.valueOf(claim.dateOfService()),
                cpts(claim));
        return count != null && count > 0;
    }

    @Override
    public long countForProviderOn(Npi npi, LocalDate dateOfService) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM claim_history WHERE provider_npi = ? AND date_of_service = ?", Long.class,
                npi.value(), Date.valueOf(dateOfService));
        return count == null ? 0 : count;
    }

    @Override
    public void record(Claim claim, ClaimStatus finalStatus) {
        jdbc.update("INSERT INTO claim_history (claim_id, member_id, provider_npi, date_of_service, cpt_codes, status)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                claim.id().value(), claim.memberId().value(), claim.providerNpi().value(),
                Date.valueOf(claim.dateOfService()), cpts(claim), finalStatus.name());
    }

    private static String cpts(Claim claim) {
        return claim.lines().stream().map(ClaimLine::cptCode).sorted().collect(Collectors.joining(","));
    }
}
