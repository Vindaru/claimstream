package com.claimstream.adjudicationservice;

import com.claimstream.adjudication.ports.MemberDirectory;
import com.claimstream.adjudication.ports.ProviderDirectory;
import com.claimstream.adjudication.pricing.FeeSchedule;
import com.claimstream.domain.CoveragePeriod;
import com.claimstream.domain.Member;
import com.claimstream.domain.MemberId;
import com.claimstream.domain.Money;
import com.claimstream.domain.Npi;
import com.claimstream.domain.Plan;
import com.claimstream.domain.Provider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Members, providers and contracted rates, held in memory: the same cast as SampleData (Maria, James,
 * Dr. Patel, Quick Care), plus synthetic covered members for load tests.
 *
 * <p>Known shortcut, fixed later: M6 moves eligibility behind its own service, and M7 makes the deductible
 * update part of a saga. Until then deductibles live in this JVM's memory, so two instances or a restart
 * would each keep their own count.
 */
@Component
public class ReferenceData implements MemberDirectory, ProviderDirectory, FeeSchedule {

    static final Plan GOLD_PPO = new Plan("GOLD-PPO", Money.of("500"), new BigDecimal("0.80"));
    private static final LocalDate PLAN_YEAR_START = LocalDate.of(2026, 1, 1);

    private final Map<MemberId, Member> members = new ConcurrentHashMap<>();
    private final Map<Npi, Provider> providers = new ConcurrentHashMap<>();
    private final Map<String, Money> rates = new ConcurrentHashMap<>();

    public ReferenceData() {
        put(new Member(new MemberId("M-1001"), "Maria Lopez", GOLD_PPO,
                CoveragePeriod.openFrom(PLAN_YEAR_START), Money.of("350")));
        put(new Member(new MemberId("M-2002"), "James Chen", GOLD_PPO,
                new CoveragePeriod(PLAN_YEAR_START, LocalDate.of(2026, 9, 30)), Money.ZERO));
        provider(new Provider(new Npi("1234567890"), "Dr. Patel Family Clinic", true), "110");
        provider(new Provider(new Npi("9876543210"), "Quick Care Clinic", true), "110");
    }

    @Override
    public Optional<Member> find(MemberId id) {
        return Optional.ofNullable(members.computeIfAbsent(id, ReferenceData::syntheticPatient));
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
        return Optional.ofNullable(rates.get(npi.value() + "/" + cptCode));
    }

    /** Quick Care's patients (M-3xxx) and load-test members (M-9xxxx): covered, deductible already met. */
    private static Member syntheticPatient(MemberId id) {
        if (!id.value().matches("M-(3\\d{3}|9\\d+)")) {
            return null; // unknown member: the eligibility rule denies the claim
        }
        return new Member(id, "Patient " + id.value(), GOLD_PPO, CoveragePeriod.openFrom(PLAN_YEAR_START),
                GOLD_PPO.deductible());
    }

    private void put(Member member) {
        members.put(member.id(), member);
    }

    private void provider(Provider provider, String officeVisitRate) {
        providers.put(provider.npi(), provider);
        rates.put(provider.npi().value() + "/99213", Money.of(officeVisitRate));
    }
}
