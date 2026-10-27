package com.claimstream.batch;

import com.claimstream.adjudication.ports.MemberDirectory;
import com.claimstream.domain.Member;
import com.claimstream.domain.MemberId;
import java.time.Duration;
import java.util.Optional;

/**
 * Decorator that adds a network-like wait to every lookup, like calling the Eligibility Service (M6) or Postgres.
 * Real intake is I/O-bound: the thread spends most of its time waiting, which is exactly where threads pay off.
 */
public final class SlowMemberDirectory implements MemberDirectory {

    private final MemberDirectory delegate;
    private final Duration latency;

    public SlowMemberDirectory(MemberDirectory delegate, Duration latency) {
        this.delegate = delegate;
        this.latency = latency;
    }

    @Override
    public Optional<Member> find(MemberId id) {
        if (!latency.isZero()) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during member lookup", e);
            }
        }
        return delegate.find(id);
    }

    @Override
    public void save(Member member) {
        delegate.save(member);
    }
}
