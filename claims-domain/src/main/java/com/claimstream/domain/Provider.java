package com.claimstream.domain;

import java.util.Objects;

/** A billing provider. An entity identified by its {@link Npi}. */
public record Provider(Npi npi, String name, boolean inNetwork) {

    public Provider {
        Objects.requireNonNull(npi, "npi");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Provider that && npi.equals(that.npi);
    }

    @Override
    public int hashCode() {
        return npi.hashCode();
    }
}
