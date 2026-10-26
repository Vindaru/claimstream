package com.claimstream.adjudication;

import java.util.ArrayList;
import java.util.List;

/**
 * The statuses a claim has passed through, oldest first. Immutable: {@link #moveTo} returns a new lifecycle,
 * which doubles as an audit trail (RECEIVED, VALIDATED, PRICED, ADJUDICATED, PAID).
 */
public record ClaimLifecycle(List<ClaimStatus> history) {

    public ClaimLifecycle {
        if (history.isEmpty()) {
            throw new IllegalArgumentException("A lifecycle starts with at least one status");
        }
        history = List.copyOf(history);
    }

    public static ClaimLifecycle received() {
        return new ClaimLifecycle(List.of(ClaimStatus.RECEIVED));
    }

    public ClaimStatus current() {
        return history.getLast();
    }

    public ClaimLifecycle moveTo(ClaimStatus target) {
        if (!current().canMoveTo(target)) {
            throw new IllegalStateException("Claim cannot move from " + current() + " to " + target);
        }
        List<ClaimStatus> next = new ArrayList<>(history);
        next.add(target);
        return new ClaimLifecycle(next);
    }
}
