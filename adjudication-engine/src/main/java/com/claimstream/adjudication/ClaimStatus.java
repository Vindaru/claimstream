package com.claimstream.adjudication;

import java.util.EnumSet;
import java.util.Set;

/**
 * State pattern: each status knows which statuses may follow it, so an illegal jump
 * (say PAID back to PRICED) is refused by the state itself instead of by scattered if-checks.
 */
public enum ClaimStatus {

    RECEIVED {
        @Override
        public Set<ClaimStatus> next() {
            return EnumSet.of(VALIDATED, DENIED, PENDED);
        }
    },
    VALIDATED {
        @Override
        public Set<ClaimStatus> next() {
            return EnumSet.of(PRICED, PENDED);
        }
    },
    PRICED {
        @Override
        public Set<ClaimStatus> next() {
            return EnumSet.of(ADJUDICATED);
        }
    },
    ADJUDICATED {
        @Override
        public Set<ClaimStatus> next() {
            return EnumSet.of(PAID);
        }
    },
    PAID,
    DENIED,
    /** Waiting on a person or a dependency; a pended claim is re-driven from the start later. */
    PENDED {
        @Override
        public Set<ClaimStatus> next() {
            return EnumSet.of(RECEIVED);
        }
    };

    /** Statuses this one may move to. Terminal statuses allow none. */
    public Set<ClaimStatus> next() {
        return EnumSet.noneOf(ClaimStatus.class);
    }

    public boolean canMoveTo(ClaimStatus target) {
        return next().contains(target);
    }
}
