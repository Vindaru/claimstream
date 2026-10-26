package com.claimstream.adjudication.benefits;

import com.claimstream.domain.Money;

/**
 * Who pays what for one claim.
 *
 * @param allowed       total allowed amount across lines
 * @param toDeductible  part of allowed the member pays toward the deductible
 * @param planPays      plan's share of what is left after the deductible
 * @param memberPays    deductible portion plus the member's coinsurance
 */
public record BenefitBreakdown(Money allowed, Money toDeductible, Money planPays, Money memberPays) {
}
