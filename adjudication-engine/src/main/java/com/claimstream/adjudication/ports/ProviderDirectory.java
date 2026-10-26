package com.claimstream.adjudication.ports;

import com.claimstream.domain.Npi;
import com.claimstream.domain.Provider;
import java.util.Optional;

/** Where the engine looks up providers and their network status. */
public interface ProviderDirectory {

    Optional<Provider> find(Npi npi);
}
