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

/**
 * Reads identity's public profiles over HTTP, with a small in-memory cache.
 *
 * <p>Both the profiles and the absences (a 404, or an id identity did not return) are cached: a profile for the
 * positive TTL, an absence for the negative one. After a failed call the client stops asking for a while
 * ({@link #BACKOFF_START} doubling to {@link #BACKOFF_MAX}); one client talks to one identity, so both calls share
 * that state. Calls already in flight that fail inside an open window neither extend it nor double the backoff.
 */
public class PublicUserClient {

    static final int CHUNK = 100;
    static final int MAX_ENTRIES = 10_000;
    static final Duration DEFAULT_NEGATIVE_TTL = Duration.ofSeconds(60);
    static final Duration BACKOFF_START = Duration.ofSeconds(1);
    static final Duration BACKOFF_MAX = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(PublicUserClient.class);
    private static final ParameterizedTypeReference<ApiResponse<PublicUser>> ONE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<PublicUser>>> MANY = new ParameterizedTypeReference<>() {};

    /** {@code user} is null for a cached absence. */
    private record Entry(PublicUser user, Instant expiresAt) {
    }

    private final RestClient rest;
    private final Clock clock;
    private final Duration ttl;
    private final Duration negativeTtl;
    private final Map<String, Entry> byName = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> byId = new ConcurrentHashMap<>();

    private final MeterRegistry meters;

    private final Object backoffLock = new Object();
    /** No request before this instant; null when identity is believed healthy. */
    private volatile Instant skipUntil;
    /** How long the next failure keeps us away. */
    private volatile Duration backoff = BACKOFF_START;

    public PublicUserClient(RestClient rest, Clock clock, Duration ttl) {
        this(rest, clock, ttl, null);
    }

    /** Absences are cached for 60 s. */
    public PublicUserClient(RestClient rest, Clock clock, Duration ttl, MeterRegistry meters) {
        this(rest, clock, ttl, DEFAULT_NEGATIVE_TTL, meters);
    }

    /**
     * @param ttl         how long a fetched profile is served from memory
     * @param negativeTtl how long an unknown username or id is remembered as absent
     * @param meters      counts {@code oj.identity.client.failures{call}} and {@code oj.identity.client.skipped{call}};
     *                    null counts nothing
     */
    public PublicUserClient(RestClient rest, Clock clock, Duration ttl, Duration negativeTtl, MeterRegistry meters) {
        this.rest = rest;
        this.clock = clock;
        this.ttl = ttl;
        this.negativeTtl = negativeTtl;
        this.meters = meters;
    }

    private void count(String name, String call) {
        if (meters != null) {
            meters.counter(name, "call", call).increment();
        }
    }

    private boolean backingOff() {
        Instant until = skipUntil;
        return until != null && clock.instant().isBefore(until);
    }

    /**
     * Opens the next skip window and doubles the backoff. A failure that lands inside a live window changes nothing:
     * its call was already in flight when an earlier one failed, so it says nothing new about identity, and a burst of
     * N concurrent failures must not jump the backoff N doublings at once.
     */
    private void failed() {
        synchronized (backoffLock) {
            Instant now = clock.instant();
            Instant until = skipUntil;
            if (until != null && now.isBefore(until)) {
                return;
            }
            skipUntil = now.plus(backoff);
            Duration doubled = backoff.multipliedBy(2);
            backoff = doubled.compareTo(BACKOFF_MAX) > 0 ? BACKOFF_MAX : doubled;
        }
    }

    private void succeeded() {
        synchronized (backoffLock) {
            skipUntil = null;
            backoff = BACKOFF_START;
        }
    }

    /**
     * The profile, or empty on 404 (remembered for the negative TTL). While identity is backing off, a name that is
     * not cached throws {@link IdentityUnavailableException} without a request; so does any other failure.
     */
    public Optional<PublicUser> byUsername(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        Entry cached = fresh(byName.get(key));
        if (cached != null) {
            return Optional.ofNullable(cached.user());
        }
        if (backingOff()) {
            count("oj.identity.client.skipped", "by_username");
            throw new IdentityUnavailableException("Identity is unavailable (backing off)", null);
        }
        try {
            ApiResponse<PublicUser> body = rest.get().uri("/api/v1/users/public/{username}", username)
                    .retrieve().body(ONE);
            PublicUser user = body == null ? null : body.getData();
            if (user == null) {
                throw new IdentityUnavailableException("Identity answered without a profile", null);
            }
            succeeded();
            put(byName, key, user);
            return Optional.of(user);
        } catch (HttpClientErrorException.NotFound e) {
            succeeded();
            put(byName, key, null);
            return Optional.empty();
        } catch (IdentityUnavailableException e) {
            failed();
            count("oj.identity.client.failures", "by_username");
            throw e;
        } catch (RuntimeException e) {
            failed();
            count("oj.identity.client.failures", "by_username");
            throw new IdentityUnavailableException("Identity is unavailable", e);
        }
    }

    /**
     * The known profiles among {@code ids}. Never throws: if identity fails or is backing off, the cached subset is
     * returned. Ids identity does not know are remembered as absent and left out of later requests.
     */
    public Map<UUID, PublicUser> byIds(Collection<UUID> ids) {
        Map<UUID, PublicUser> result = new HashMap<>();
        List<UUID> misses = new ArrayList<>();
        for (UUID id : ids) {
            Entry cached = fresh(byId.get(id));
            if (cached == null) {
                misses.add(id);
            } else if (cached.user() != null) {
                result.put(id, cached.user());
            }
        }
        if (misses.isEmpty()) {
            return result;
        }
        if (backingOff()) {
            count("oj.identity.client.skipped", "by_ids");
            return result;
        }
        try {
            for (int from = 0; from < misses.size(); from += CHUNK) {
                List<UUID> chunk = misses.subList(from, Math.min(from + CHUNK, misses.size()));
                String joined = chunk.stream().map(UUID::toString).collect(Collectors.joining(","));
                ApiResponse<List<PublicUser>> body = rest.get()
                        .uri(b -> b.path("/api/v1/users/public").queryParam("ids", joined).build())
                        .retrieve().body(MANY);
                succeeded();
                if (body != null && body.getData() != null) {
                    for (PublicUser user : body.getData()) {
                        put(byId, user.id(), user);
                        result.put(user.id(), user);
                    }
                }
                for (UUID id : chunk) {
                    if (!result.containsKey(id)) {
                        put(byId, id, null);
                    }
                }
            }
        } catch (RuntimeException e) {
            failed();
            count("oj.identity.client.failures", "by_ids");
            log.warn("Identity profiles unavailable, serving the cached subset: {}", e.toString());
        }
        return result;
    }

    /** The entry if it has not expired, whether it holds a profile or an absence. */
    private Entry fresh(Entry entry) {
        return entry != null && clock.instant().isBefore(entry.expiresAt()) ? entry : null;
    }

    /** Caches {@code user}, or an absence when null. Both share the entry cap. */
    private <K> void put(Map<K, Entry> cache, K key, PublicUser user) {
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new Entry(user, clock.instant().plus(user == null ? negativeTtl : ttl)));
    }
}
