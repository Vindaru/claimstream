package com.claimstream.batch;

import com.claimstream.adjudication.ports.MemberDirectory;
import com.claimstream.domain.CoveragePeriod;
import com.claimstream.domain.Member;
import com.claimstream.domain.MemberId;
import com.claimstream.domain.Money;
import com.claimstream.domain.Plan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe member directory: Maria, James (coverage ended 2026-09-30) and {@link #SYNTHETIC_MEMBERS}
 * generated members M-100000 onward. Backed by {@link ConcurrentHashMap} because many threads read it at once.
 */
public final class Roster implements MemberDirectory {

    public static final int SYNTHETIC_MEMBERS = 10_000;
    public static final int FIRST_SYNTHETIC_ID = 100_000;

    private static final Plan GOLD_PPO = new Plan("GOLD-PPO", Money.of("500"), new BigDecimal("0.80"));
    private static final LocalDate PLAN_YEAR_START = LocalDate.of(2026, 1, 1);

    private final Map<MemberId, Member> members = new ConcurrentHashMap<>();

    public static Roster standard() {
        Roster roster = new Roster();
        roster.save(new Member(new MemberId("M-1001"), "Maria Lopez", GOLD_PPO,
                CoveragePeriod.openFrom(PLAN_YEAR_START), Money.of("350")));
        roster.save(new Member(new MemberId("M-2002"), "James Chen", GOLD_PPO,
                new CoveragePeriod(PLAN_YEAR_START, LocalDate.of(2026, 9, 30)), Money.ZERO));
        for (int i = 0; i < SYNTHETIC_MEMBERS; i++) {
            MemberId id = new MemberId("M-" + (FIRST_SYNTHETIC_ID + i));
            roster.save(new Member(id, "Member " + id.value(), GOLD_PPO,
                    CoveragePeriod.openFrom(PLAN_YEAR_START), Money.ZERO));
        }
        return roster;
    }

    @Override
    public Optional<Member> find(MemberId id) {
        return Optional.ofNullable(members.get(id));
    }

    @Override
    public void save(Member member) {
        members.put(member.id(), member);
    }
}
