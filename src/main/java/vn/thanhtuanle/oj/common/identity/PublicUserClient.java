package vn.thanhtuanle.oj.common.identity;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import vn.thanhtuanle.oj.common.web.payload.ApiResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Reads identity's public profiles over HTTP, with a small in-memory cache. */
public class PublicUserClient {

    static final int CHUNK = 100;
    static final int MAX_ENTRIES = 10_000;

    private static final Logger log = LoggerFactory.getLogger(PublicUserClient.class);
    private static final ParameterizedTypeReference<ApiResponse<PublicUser>> ONE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<PublicUser>>> MANY = new ParameterizedTypeReference<>() {};

    private record Entry(PublicUser user, Instant expiresAt) {
    }

    private final RestClient rest;
    private final Clock clock;
    private final Duration ttl;
    private final Map<String, Entry> byName = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> byId = new ConcurrentHashMap<>();

    private final MeterRegistry meters;

    public PublicUserClient(RestClient rest, Clock clock, Duration ttl) {
        this(rest, clock, ttl, null);
    }

    /** @param meters counts failures as {@code oj.identity.client.failures{call}}; null counts nothing */
    public PublicUserClient(RestClient rest, Clock clock, Duration ttl, MeterRegistry meters) {
        this.rest = rest;
        this.clock = clock;
        this.ttl = ttl;
        this.meters = meters;
    }

    private void countFailure(String call) {
        if (meters != null) {
            meters.counter("oj.identity.client.failures", "call", call).increment();
        }
    }

    /** The profile, or empty on 404 (not cached). Any other failure throws {@link IdentityUnavailableException}. */
    public Optional<PublicUser> byUsername(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        PublicUser cached = fresh(byName.get(key));
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            ApiResponse<PublicUser> body = rest.get().uri("/api/v1/users/public/{username}", username)
                    .retrieve().body(ONE);
            PublicUser user = body == null ? null : body.getData();
            if (user == null) {
                throw new IdentityUnavailableException("Identity answered without a profile", null);
            }
            put(byName, key, user);
            return Optional.of(user);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (IdentityUnavailableException e) {
            countFailure("by_username");
            throw e;
        } catch (RuntimeException e) {
            countFailure("by_username");
            throw new IdentityUnavailableException("Identity is unavailable", e);
        }
    }

    /** The known profiles among {@code ids}. Never throws: if identity fails, the cached subset is returned. */
    public Map<UUID, PublicUser> byIds(Collection<UUID> ids) {
        Map<UUID, PublicUser> result = new HashMap<>();
        List<UUID> misses = new ArrayList<>();
        for (UUID id : ids) {
            PublicUser cached = fresh(byId.get(id));
            if (cached != null) {
                result.put(id, cached);
            } else {
                misses.add(id);
            }
        }
        try {
            for (int from = 0; from < misses.size(); from += CHUNK) {
                String joined = misses.subList(from, Math.min(from + CHUNK, misses.size())).stream()
                        .map(UUID::toString).collect(Collectors.joining(","));
                ApiResponse<List<PublicUser>> body = rest.get()
                        .uri(b -> b.path("/api/v1/users/public").queryParam("ids", joined).build())
                        .retrieve().body(MANY);
                if (body != null && body.getData() != null) {
                    for (PublicUser user : body.getData()) {
                        put(byId, user.id(), user);
                        result.put(user.id(), user);
                    }
                }
            }
        } catch (RuntimeException e) {
            countFailure("by_ids");
            log.warn("Identity profiles unavailable, serving the cached subset: {}", e.toString());
        }
        return result;
    }

    private PublicUser fresh(Entry entry) {
        return entry != null && clock.instant().isBefore(entry.expiresAt()) ? entry.user() : null;
    }

    private <K> void put(Map<K, Entry> cache, K key, PublicUser user) {
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new Entry(user, clock.instant().plus(ttl)));
    }
}
