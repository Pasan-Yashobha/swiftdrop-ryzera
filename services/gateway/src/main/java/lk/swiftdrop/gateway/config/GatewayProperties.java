package lk.swiftdrop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * All gateway settings. Every value can be overridden with an environment variable
 * (see application.yml), so nothing is hard-coded.
 *
 * @param issuer             expected "iss" claim of Identity's access tokens
 * @param jwksUri            where Identity publishes its public keys
 * @param corsAllowedOrigins origins of the Angular apps
 * @param uris               base URI of each backend service, keyed by service name
 * @param rateLimits         token-bucket limits per caller type
 * @param devRoutesEnabled   expose Merchant's /api/v1/dev/** (dev tokens). Keep false outside local development
 */
@ConfigurationProperties(prefix = "swiftdrop.gateway")
public record GatewayProperties(
        String issuer,
        String jwksUri,
        List<String> corsAllowedOrigins,
        Map<String, String> uris,
        RateLimits rateLimits,
        boolean devRoutesEnabled) {

    public record RateLimits(Limit user, Limit merchantApi, Limit auth, Limit publicApi) {
    }

    /**
     * Token bucket: {@code replenishRate} tokens per second, up to {@code burstCapacity},
     * each request costs {@code requestedTokens}.
     */
    public record Limit(int replenishRate, int burstCapacity, int requestedTokens) {
    }

    public String uri(String service) {
        String value = uris.get(service);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("No URI configured for service: " + service);
        }
        return value;
    }
}
