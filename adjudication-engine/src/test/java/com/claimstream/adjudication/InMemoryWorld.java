package com.claimstream.adjudication;

import com.claimstream.adjudication.ports.ClaimHistory;
import com.claimstream.adjudication.ports.EligibilityUnavailableException;
import com.claimstream.adjudication.ports.MemberDirectory;
import com.claimstream.adjudication.ports.ProviderDirectory;
import com.claimstream.adjudication.pricing.FeeSchedule;
import com.claimstream.domain.Claim;
import com.claimstream.domain.ClaimLine;
import com.claimstream.domain.Member;
import com.claimstream.domain.MemberId;
import com.claimstream.domain.Money;
import com.claimstream.domain.Npi;
import com.claimstream.domain.Provider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Map-backed fakes for every port, so the whole engine runs in memory in milliseconds. */
final class InMemoryWorld implements MemberDirectory, ProviderDirectory, FeeSchedule, ClaimHistory {

    private final Map<MemberId, Member> members = new HashMap<>();
    private final Map<Npi, Provider> providers = new HashMap<>();
    private final Map<String, Money> rates = new HashMap<>();
    private final List<Claim> paid = new ArrayList<>();
    private final List<Claim> seen = new ArrayList<>();
    private boolean eligibilityDown;

    InMemoryWorld member(Member member) {
        members.put(member.id(), member);
        return this;
    }

    InMemoryWorld provider(Provider provider) {
        providers.put(provider.npi(), provider);
        return this;
    }

    InMemoryWorld rate(Npi npi, String cpt, String amount) {
        rates.put(npi + "/" + cpt, Money.of(amount));
        return this;
    }

    /** Simulates the Eligibility Service being unreachable (CLM-5005). */
    void eligibilityDown(boolean down) {
        eligibilityDown = down;
    }

    @Override
    public Optional<Member> find(MemberId id) {
        if (eligibilityDown) {
            throw new EligibilityUnavailableException("Eligibility source did not respond");
        }
        return Optional.ofNullable(members.get(id));
    }

    @Override
    public void save(Member member) {
        members.put(member.id(), member);
    }

    @Override
    public Optional<Provider> find(Npi npi) {
        return Optional.ofNullable(providers.get(npi));
    }

    @Override
    public Optional<Money> rateFor(Npi npi, String cptCode) {
        return Optional.ofNullable(rates.get(npi + "/" + cptCode));
    }

    @Override
    public boolean hasPaidMatching(Claim claim) {
        return paid.stream().anyMatch(p -> p.memberId().equals(claim.memberId())
                && p.providerNpi().equals(claim.providerNpi())
                && p.dateOfService().equals(claim.dateOfService())
                && cpts(p).equals(cpts(claim)));
    }

    @Override
    public long countForProviderOn(Npi npi, LocalDate dateOfService) {
        return seen.stream()
                .filter(c -> c.providerNpi().equals(npi) && c.dateOfService().equals(dateOfService))
                .count();
    }

    @Override
    public void record(Claim claim, ClaimStatus finalStatus) {
        seen.add(claim);
        if (finalStatus == ClaimStatus.PAID) {
            paid.add(claim);
        }
    }

    Member memberNow(MemberId id) {
        return members.get(id);
    }

    private static List<String> cpts(Claim claim) {
        return claim.lines().stream().map(ClaimLine::cptCode).sorted().toList();
    }
}
