package com.claimstream.adjudication;

import com.claimstream.adjudication.benefits.BenefitBreakdown;
import com.claimstream.adjudication.benefits.BenefitCalculator;
import com.claimstream.adjudication.ports.ClaimHistory;
import com.claimstream.adjudication.ports.MemberDirectory;
import com.claimstream.adjudication.ports.ProviderDirectory;
import com.claimstream.adjudication.pricing.FeeSchedule;
import com.claimstream.adjudication.pricing.FeeSchedulePricing;
import com.claimstream.adjudication.pricing.NoFeeScheduleException;
import com.claimstream.adjudication.pricing.PercentOfBilledPricing;
import com.claimstream.adjudication.pricing.PricingStrategy;
import com.claimstream.adjudication.pricing.PricingStrategyFactory;
import com.claimstream.adjudication.rules.DuplicateClaimRule;
import com.claimstream.adjudication.rules.EligibilityRule;
import com.claimstream.adjudication.rules.MedicalNecessityRule;
import com.claimstream.adjudication.rules.ProviderVisitLimitRule;
import com.claimstream.adjudication.rules.RuleChain;
import com.claimstream.adjudication.rules.RuleOutcome;
import com.claimstream.domain.Claim;
import com.claimstream.domain.Member;
import com.claimstream.domain.Money;
import com.claimstream.domain.Provider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Drives one claim through its lifecycle: RECEIVED → (rules) VALIDATED → (pricing) PRICED →
 * (benefits) ADJUDICATED → PAID, or stops at DENIED / PENDED with a reason.
 *
 * <p>The engine only orchestrates. Each decision lives in its own class (rule, strategy, calculator),
 * and every dependency is an interface, so tests run it with in-memory maps.
 */
public final class AdjudicationEngine {

    static final Set<String> COVERED_CPT_CODES = Set.of("99212", "99213", "99214", "99215", "36415", "80053");
    static final int MAX_CLAIMS_PER_PROVIDER_PER_DAY = 25;
    static final BigDecimal OUT_OF_NETWORK_ALLOWED_SHARE = new BigDecimal("0.60");

    private final RuleChain rules;
    private final MemberDirectory members;
    private final ProviderDirectory providers;
    private final PricingStrategyFactory pricing;
    private final BenefitCalculator benefits;
    private final ClaimHistory history;

    public AdjudicationEngine(RuleChain rules, MemberDirectory members, ProviderDirectory providers,
                              PricingStrategyFactory pricing, BenefitCalculator benefits, ClaimHistory history) {
        this.rules = rules;
        this.members = members;
        this.providers = providers;
        this.pricing = pricing;
        this.benefits = benefits;
        this.history = history;
    }

    /** The standard rule order: eligibility → duplicate → medical necessity → provider visit limit. */
    public static AdjudicationEngine withStandardRules(MemberDirectory members, ProviderDirectory providers,
                                                       FeeSchedule feeSchedule, ClaimHistory history) {
        RuleChain chain = new RuleChain(List.of(
                new EligibilityRule(members),
                new DuplicateClaimRule(history),
                new MedicalNecessityRule(COVERED_CPT_CODES),
                new ProviderVisitLimitRule(history, MAX_CLAIMS_PER_PROVIDER_PER_DAY)));
        PricingStrategyFactory pricing = new PricingStrategyFactory(
                new FeeSchedulePricing(feeSchedule), new PercentOfBilledPricing(OUT_OF_NETWORK_ALLOWED_SHARE));
        return new AdjudicationEngine(chain, members, providers, pricing, new BenefitCalculator(), history);
    }

    public AdjudicationResult adjudicate(Claim claim) {
        ClaimLifecycle lifecycle = ClaimLifecycle.received();

        RuleOutcome outcome = rules.evaluate(claim);
        if (outcome.stopsTheChain()) {
            ClaimStatus stop = outcome.verdict() == RuleOutcome.Verdict.DENY ? ClaimStatus.DENIED : ClaimStatus.PENDED;
            return finish(claim, AdjudicationResult.stopped(
                    claim.id(), lifecycle.moveTo(stop), outcome.reasonCode(), outcome.message()));
        }
        lifecycle = lifecycle.moveTo(ClaimStatus.VALIDATED);

        Optional<Provider> provider = providers.find(claim.providerNpi());
        if (provider.isEmpty()) {
            return finish(claim, AdjudicationResult.stopped(claim.id(), lifecycle.moveTo(ClaimStatus.PENDED),
                    "PROVIDER_NOT_FOUND", "No provider " + claim.providerNpi()));
        }
        Money allowed;
        try {
            allowed = price(claim, provider.get());
        } catch (NoFeeScheduleException e) {
            return finish(claim, AdjudicationResult.stopped(claim.id(), lifecycle.moveTo(ClaimStatus.PENDED),
                    "NO_FEE_SCHEDULE", e.getMessage()));
        }
        lifecycle = lifecycle.moveTo(ClaimStatus.PRICED);

        Member member = members.find(claim.memberId()).orElseThrow(); // the eligibility rule already found them
        BenefitBreakdown breakdown = benefits.calculate(member, allowed);
        lifecycle = lifecycle.moveTo(ClaimStatus.ADJUDICATED);
        members.save(member.withDeductibleApplied(breakdown.toDeductible()));

        // M2 marks the claim paid straight away; M7 replaces this with a payment saga.
        lifecycle = lifecycle.moveTo(ClaimStatus.PAID);
        return finish(claim, AdjudicationResult.paid(claim.id(), lifecycle, breakdown));
    }

    private Money price(Claim claim, Provider provider) {
        PricingStrategy strategy = pricing.forProvider(provider);
        return claim.lines().stream()
                .map(line -> strategy.allowedFor(line, provider))
                .reduce(Money.ZERO, Money::plus);
    }

    private AdjudicationResult finish(Claim claim, AdjudicationResult result) {
        history.record(claim, result.status());
        return result;
    }
}
