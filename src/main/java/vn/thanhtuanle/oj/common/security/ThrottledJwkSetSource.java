package vn.thanhtuanle.oj.common.security;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * A JWKS source that never expires the keys it holds and refetches only when a key id is unknown, at most once per
 * interval. Known keys verify through an issuer outage of any length; a flood of unknown key ids costs one fetch per
 * interval, not one per token; a failed fetch keeps the last good set.
 */
public final class ThrottledJwkSetSource implements JWKSource<SecurityContext> {

    private static final Logger log = LoggerFactory.getLogger(ThrottledJwkSetSource.class);

    private final Supplier<JWKSet> fetch;
    private final Duration refetchInterval;
    private final Duration coldStartInterval;
    private final Clock clock;

    private volatile JWKSet keys;
    private Instant lastAttempt;

    public ThrottledJwkSetSource(Supplier<JWKSet> fetch, Duration refetchInterval, Duration coldStartInterval, Clock clock) {
        this.fetch = fetch;
        this.refetchInterval = refetchInterval;
        this.coldStartInterval = coldStartInterval;
        this.clock = clock;
    }

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
        JWKSet current = keys;
        if (current != null) {
            List<JWK> match = selector.select(current);
            if (!match.isEmpty()) {
                return match;
            }
        }
        synchronized (this) {
            // another thread may have refreshed the set while this one waited for the lock
            if (keys != null && keys != current) {
                List<JWK> match = selector.select(keys);
                if (!match.isEmpty()) {
                    return match;
                }
            }
            Instant now = clock.instant();
            Duration interval = keys == null ? coldStartInterval : refetchInterval;
            if (lastAttempt != null && now.isBefore(lastAttempt.plus(interval))) {
                return List.of();
            }
            lastAttempt = now;
            try {
                keys = fetch.get();
            } catch (RuntimeException e) {
                log.warn("JWKS refetch failed, keeping the last good key set: {}", e.getMessage());
                // The cause travels with the rejection so a misconfigured client (TLS, DNS) is diagnosable.
                throw new KeySourceException("Could not fetch the JWKS: " + e.getMessage(), e);
            }
            return selector.select(keys);
        }
    }
}
