package com.claimstream.domain.events;

import java.math.BigDecimal;

/**
 * "The rules engine decided this claim." Amounts are null unless the claim was paid.
 *
 * <p>This is the fact the AI layer (M8.5) explains. It never changes it.
 */
public record ClaimAdjudicated(String claimId, String memberId, String status, String reasonCode, String message,
                               BigDecimal allowed, BigDecimal planPays, BigDecimal memberPays) {
}
