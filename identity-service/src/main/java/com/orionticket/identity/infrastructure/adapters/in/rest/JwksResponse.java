package com.orionticket.identity.infrastructure.adapters.in.rest;

import java.util.List;

public record JwksResponse(List<Jwk> keys) {

    public record Jwk(
            String kty,
            String use,
            String kid,
            String alg,
            String n,
            String e
    ) {
    }
}
