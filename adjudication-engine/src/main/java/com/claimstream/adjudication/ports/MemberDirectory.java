package com.claimstream.adjudication.ports;

import com.claimstream.domain.Member;
import com.claimstream.domain.MemberId;
import java.util.Optional;

/**
 * Where the engine looks up members. An interface (a "port") so the engine doesn't care whether the answer
 * comes from a map in a test, a database (M4) or the Eligibility Service over gRPC (M6). Dependency Inversion.
 */
public interface MemberDirectory {

    /** @throws EligibilityUnavailableException when the source of truth can't be reached */
    Optional<Member> find(MemberId id);

    void save(Member member);
}
