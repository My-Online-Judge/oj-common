package vn.thanhtuanle.oj.common.identity;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PublicUserClientTest {

    private static final String BASE = "http://identity";

    private MutableClock clock;
    private MockRestServiceServer server;
    private PublicUserClient client;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock();
        meters = new SimpleMeterRegistry();
        client = new PublicUserClient(builder.build(), clock, Duration.ofMinutes(5), Duration.ofSeconds(60), meters);
    }

    private static String user(UUID id, String username) {
        return "{\"id\":\"" + id + "\",\"username\":\"" + username + "\",\"name\":\"N " + username
                + "\",\"avatar\":null,\"createdAt\":\"2026-01-02T03:04:05\"}";
    }

    private static String envelope(String data) {
        return "{\"status\":200,\"message\":\"Success\",\"data\":" + data + ",\"timestamp\":\"20261020000000\"}";
    }

    @Test
    void byUsernameReturnsTheProfile() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE + "/api/v1/users/public/alice"))
                .andRespond(withSuccess(envelope(user(id, "alice")), MediaType.APPLICATION_JSON));

        PublicUser found = client.byUsername("alice").orElseThrow();

        assertThat(found.id()).isEqualTo(id);
        assertThat(found.username()).isEqualTo("alice");
        assertThat(found.avatar()).isNull();
        assertThat(found.createdAt()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        // cached per lower-cased username: no second request
        assertThat(client.byUsername("Alice")).isPresent();
        server.verify();
    }

    private static final String NOT_FOUND_BODY = "{\"status\":404,\"message\":\"Not found\"}";

    private void expectNotFound(String name) {
        server.expect(requestTo(BASE + "/api/v1/users/public/" + name))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(NOT_FOUND_BODY));
    }

    private void expectServerError() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private double skipped(String call) {
        return meters.counter("oj.identity.client.skipped", "call", call).count();
    }

    @Test
    void unknownUsernameIsEmpty() {
        expectNotFound("ghost");

        assertThat(client.byUsername("ghost")).isEmpty();
        server.verify();
    }

    @Test
    void anUnknownUsernameIsNotAskedAgainForAMinute() {
        expectNotFound("ghost");
        expectNotFound("ghost");

        assertThat(client.byUsername("ghost")).isEmpty();
        clock.advance(Duration.ofSeconds(59));
        assertThat(client.byUsername("Ghost")).isEmpty(); // cached absence, no request
        clock.advance(Duration.ofSeconds(2)); // 61 s after the 404
        assertThat(client.byUsername("ghost")).isEmpty(); // second request
        server.verify();
    }

    @Test
    void unknownIdsAreNotAskedAgainForAMinute() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<String> asked = new ArrayList<>();
        server.expect(method(HttpMethod.GET))
                .andExpect(request -> asked.add(request.getURI().getRawQuery()))
                .andRespond(withSuccess(envelope("[" + user(a, "alice") + "]"), MediaType.APPLICATION_JSON));

        assertThat(client.byIds(List.of(a, b))).containsOnlyKeys(a);
        clock.advance(Duration.ofSeconds(30));
        assertThat(client.byIds(List.of(a, b))).containsOnlyKeys(a); // a cached, b a cached absence: no request

        server.verify();
        assertThat(asked).hasSize(1);
    }

    @Test
    void cachedAbsencesAreLeftOutOfTheRequest() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<String> asked = new ArrayList<>();
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(envelope("[]"), MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.GET))
                .andExpect(request -> asked.add(request.getURI().getRawQuery()))
                .andRespond(withSuccess(envelope("[]"), MediaType.APPLICATION_JSON));

        client.byIds(List.of(a));
        client.byIds(List.of(a, b, c));

        server.verify();
        assertThat(asked).containsExactly("ids=" + b + "," + c);
    }

    @Test
    void anAbsenceExpiresAfterTheNegativeTtl() {
        UUID a = UUID.randomUUID();
        server.expect(ExpectedCount.twice(), method(HttpMethod.GET))
                .andRespond(withSuccess(envelope("[]"), MediaType.APPLICATION_JSON));

        client.byIds(List.of(a));
        clock.advance(Duration.ofSeconds(61));
        client.byIds(List.of(a));

        server.verify();
    }

    @Test
    void aPositiveAnswerReplacesAnAbsence() {
        UUID id = UUID.randomUUID();
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(envelope("[]"), MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(envelope("[" + user(id, "late") + "]"), MediaType.APPLICATION_JSON));

        assertThat(client.byIds(List.of(id))).isEmpty(); // absent for a minute
        clock.advance(Duration.ofSeconds(61));
        assertThat(client.byIds(List.of(id))).containsKey(id); // now known: replaces the absence
        clock.advance(Duration.ofSeconds(61)); // past the negative TTL, inside the positive one
        assertThat(client.byIds(List.of(id))).containsKey(id); // still served, no request
        server.verify();
    }

    @Test
    void aPositiveUsernameAnswerReplacesAnAbsence() {
        UUID id = UUID.randomUUID();
        expectNotFound("late");
        server.expect(requestTo(BASE + "/api/v1/users/public/late"))
                .andRespond(withSuccess(envelope(user(id, "late")), MediaType.APPLICATION_JSON));

        assertThat(client.byUsername("late")).isEmpty();
        clock.advance(Duration.ofSeconds(61));
        assertThat(client.byUsername("late")).isPresent();
        clock.advance(Duration.ofSeconds(61));
        assertThat(client.byUsername("late")).isPresent(); // positive entry, no request
        server.verify();
    }

    @Test
    void aFailureBacksOffOneSecondThenDoubles() {
        expectServerError();
        expectServerError();
        server.expect(requestTo(BASE + "/api/v1/users/public/alice")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.byUsername("alice")).isInstanceOf(IdentityUnavailableException.class); // t0
        clock.advance(Duration.ofMillis(500));
        assertThatThrownBy(() -> client.byUsername("alice")).isInstanceOf(IdentityUnavailableException.class)
                .hasMessage("Identity is unavailable (backing off)").hasNoCause(); // no request
        clock.advance(Duration.ofMillis(600)); // t0 + 1.1 s
        assertThatThrownBy(() -> client.byUsername("alice")).isInstanceOf(IdentityUnavailableException.class); // 500 again
        clock.advance(Duration.ofMillis(1400)); // t0 + 2.5 s, inside the 2 s window that ends at 3.1 s
        assertThatThrownBy(() -> client.byUsername("alice")).hasMessage("Identity is unavailable (backing off)");
        clock.advance(Duration.ofMillis(700)); // t0 + 3.2 s
        assertThatThrownBy(() -> client.byUsername("alice")).isInstanceOf(IdentityUnavailableException.class);

        server.verify();
        assertThat(failures("by_username")).isEqualTo(3.0); // skipped calls are not failures
        assertThat(skipped("by_username")).isEqualTo(2.0);
    }

    @Test
    void theBackoffStopsAt30Seconds() {
        // 10 consecutive failures, then the request once the 10th one's window is over
        server.expect(ExpectedCount.times(11), method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        for (int i = 0; i < 9; i++) {
            assertThatThrownBy(() -> client.byUsername("alice")).hasMessage("Identity is unavailable");
            clock.advance(PublicUserClient.BACKOFF_MAX); // always past the window
        }

        // the 10th consecutive failure: the window would be 512 s uncapped
        assertThatThrownBy(() -> client.byUsername("alice")).hasMessage("Identity is unavailable");
        clock.advance(PublicUserClient.BACKOFF_MAX.minusMillis(1));
        assertThatThrownBy(() -> client.byUsername("alice")).hasMessage("Identity is unavailable (backing off)");
        clock.advance(Duration.ofMillis(1)); // exactly 30 s after the failure: the window is over
        assertThatThrownBy(() -> client.byUsername("alice")).hasMessage("Identity is unavailable");
        server.verify();
    }

    /**
     * Identity goes down while three calls are in flight: all three fail after the first opened its window. Only the
     * first one counts, so the window is 1 s (not 8 s), and the next failure after it doubles once, to 2 s.
     */
    @Test
    void concurrentFailuresInsideAWindowDoNotDoubleTheBackoff() throws Exception {
        int inFlight = 3;
        CountDownLatch allPastTheCheck = new CountDownLatch(inFlight);
        AtomicInteger requests = new AtomicInteger();
        PublicUserClient burst = new PublicUserClient(RestClient.builder().baseUrl(BASE)
                .requestInterceptor((request, body, execution) -> {
                    if (requests.incrementAndGet() <= inFlight) {
                        // every call has passed the backoff check before any of them fails
                        allPastTheCheck.countDown();
                        try {
                            if (!allPastTheCheck.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("the calls did not overlap");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    throw new IOException("Connection refused");
                }).build(), clock, Duration.ofMinutes(5), Duration.ofSeconds(60), meters);

        ExecutorService pool = Executors.newFixedThreadPool(inFlight);
        try {
            List<Future<Throwable>> calls = new ArrayList<>();
            for (int i = 0; i < inFlight; i++) {
                String name = "user" + i;
                calls.add(pool.submit(() -> {
                    try {
                        burst.byUsername(name);
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            for (Future<Throwable> call : calls) {
                assertThat(call.get(10, TimeUnit.SECONDS)).isInstanceOf(IdentityUnavailableException.class)
                        .hasMessage("Identity is unavailable");
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(requests).hasValue(inFlight);
        assertThat(failures("by_username")).isEqualTo(3.0);

        clock.advance(Duration.ofMillis(999));
        assertThatThrownBy(() -> burst.byUsername("late")).hasMessage("Identity is unavailable (backing off)");
        clock.advance(Duration.ofMillis(1)); // 1 s after the burst: the window is over
        assertThatThrownBy(() -> burst.byUsername("late")).hasMessage("Identity is unavailable");
        assertThat(requests).hasValue(inFlight + 1);
        clock.advance(Duration.ofMillis(1999)); // the burst doubled the backoff once: this window is 2 s
        assertThatThrownBy(() -> burst.byUsername("late")).hasMessage("Identity is unavailable (backing off)");
        clock.advance(Duration.ofMillis(1));
        assertThatThrownBy(() -> burst.byUsername("late")).hasMessage("Identity is unavailable");
        assertThat(requests).hasValue(inFlight + 2);
    }

    @Test
    void aSuccessEndsTheBackoff() {
        UUID id = UUID.randomUUID();
        expectServerError();
        server.expect(requestTo(BASE + "/api/v1/users/public/alice"))
                .andRespond(withSuccess(envelope(user(id, "alice")), MediaType.APPLICATION_JSON));
        expectServerError();
        server.expect(requestTo(BASE + "/api/v1/users/public/bob")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.byUsername("alice")).isInstanceOf(IdentityUnavailableException.class);
        clock.advance(Duration.ofMillis(1100));
        assertThat(client.byUsername("alice")).isPresent(); // success: backoff back to 1 s
        assertThatThrownBy(() -> client.byUsername("bob")).isInstanceOf(IdentityUnavailableException.class);
        clock.advance(Duration.ofMillis(1100)); // would still be inside a doubled 2 s window
        assertThatThrownBy(() -> client.byUsername("bob")).hasMessage("Identity is unavailable");
        server.verify();
    }

    @Test
    void aNotFoundCountsAsSuccess() {
        expectServerError();
        expectNotFound("ghost");
        expectServerError();
        expectServerError();

        assertThatThrownBy(() -> client.byUsername("alice")).isInstanceOf(IdentityUnavailableException.class);
        clock.advance(Duration.ofMillis(1100));
        assertThat(client.byUsername("ghost")).isEmpty(); // a 404 is an answer: ends the backoff
        assertThatThrownBy(() -> client.byUsername("alice")).hasMessage("Identity is unavailable");
        clock.advance(Duration.ofMillis(1100)); // a reset backoff is 1 s again, not 2 s
        assertThatThrownBy(() -> client.byUsername("alice")).hasMessage("Identity is unavailable");
        server.verify();
        assertThat(skipped("by_username")).isZero();
    }

    @Test
    void byUsernameServesCachedHitsWhileBackingOff() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE + "/api/v1/users/public/alice"))
                .andRespond(withSuccess(envelope(user(id, "alice")), MediaType.APPLICATION_JSON));
        expectNotFound("ghost");
        server.expect(requestTo(BASE + "/api/v1/users/public/bob")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        client.byUsername("alice");
        client.byUsername("ghost");
        assertThatThrownBy(() -> client.byUsername("bob")).isInstanceOf(IdentityUnavailableException.class);

        assertThat(client.byUsername("alice")).isPresent(); // cached, backing off
        assertThat(client.byUsername("ghost")).isEmpty(); // cached absence
        assertThat(skipped("by_username")).isZero();
        server.verify();
    }

    @Test
    void byIdsServesTheCachedSubsetWhileBackingOff() {
        UUID cached = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(envelope("[" + user(cached, "carol") + "]"), MediaType.APPLICATION_JSON));
        expectServerError();

        client.byIds(List.of(cached));
        assertThat(client.byIds(List.of(cached, other))).containsOnlyKeys(cached); // fails: backoff starts
        clock.advance(Duration.ofMillis(500));
        assertThat(client.byIds(List.of(cached, other))).containsOnlyKeys(cached); // no request

        server.verify();
    }

    @Test
    void theBackoffIsSharedByBothCalls() {
        expectServerError();

        client.byIds(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> client.byUsername("alice")).hasMessage("Identity is unavailable (backing off)");
        assertThat(skipped("by_username")).isEqualTo(1.0);
        server.verify();
    }

    @Test
    void skippedCallsAreCounted() {
        expectServerError();

        client.byIds(List.of(UUID.randomUUID()));
        client.byIds(List.of(UUID.randomUUID()));

        assertThat(skipped("by_ids")).isEqualTo(1.0);
        assertThat(failures("by_ids")).isEqualTo(1.0);
        assertThat(skipped("by_username")).isZero();
    }

    @Test
    void aChunkThatFailsCachesNoAbsences() {
        List<UUID> ids = IntStream.range(0, 150).mapToObj(i -> UUID.randomUUID()).toList();
        List<Integer> sizes = new ArrayList<>();
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(envelope("[]"), MediaType.APPLICATION_JSON));
        expectServerError();
        server.expect(method(HttpMethod.GET))
                .andExpect(request -> sizes.add(request.getURI().getRawQuery().substring(4).split(",").length))
                .andRespond(withSuccess(envelope("[]"), MediaType.APPLICATION_JSON));

        client.byIds(ids); // chunk 1 answered (100 absences cached), chunk 2 fails
        clock.advance(Duration.ofSeconds(2));
        client.byIds(ids); // only the failed chunk's 50 ids are asked

        server.verify();
        assertThat(sizes).containsExactly(50);
    }

    @Test
    void identityErrorOnUsernameThrows() {
        server.expect(requestTo(BASE + "/api/v1/users/public/alice"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.byUsername("alice"))
                .isInstanceOf(IdentityUnavailableException.class)
                .isInstanceOf(RuntimeException.class);
        assertThat(failures("by_username")).isEqualTo(1.0);
        assertThat(failures("by_ids")).isZero();
    }

    @Test
    void aNotFoundUsernameIsNotAFailure() {
        server.expect(requestTo(BASE + "/api/v1/users/public/ghost"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404,\"message\":\"Not found\"}"));

        assertThat(client.byUsername("ghost")).isEmpty();

        assertThat(failures("by_username")).isZero();
    }

    private double failures(String call) {
        return meters.counter("oj.identity.client.failures", "call", call).count();
    }

    @Test
    void idsAreCachedForFiveMinutes() {
        UUID id = UUID.randomUUID();
        server.expect(ExpectedCount.twice(), method(HttpMethod.GET))
                .andRespond(withSuccess(envelope("[" + user(id, "bob") + "]"), MediaType.APPLICATION_JSON));

        assertThat(client.byIds(Set.of(id))).containsKey(id);
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(client.byIds(Set.of(id))).containsKey(id); // cached
        clock.advance(Duration.ofSeconds(2)); // 5 min 1 s after the first fetch
        assertThat(client.byIds(Set.of(id))).containsKey(id); // second request
        server.verify();
    }

    @Test
    void idsAreFetchedInChunksOf100() {
        List<UUID> ids = IntStream.range(0, 150).mapToObj(i -> UUID.randomUUID()).toList();
        List<Integer> sizes = new ArrayList<>();
        server.expect(ExpectedCount.twice(), method(HttpMethod.GET))
                .andExpect(request -> {
                    String q = request.getURI().getRawQuery();
                    assertThat(q).startsWith("ids=");
                    sizes.add(q.substring(4).split(",").length);
                })
                .andRespond(withSuccess(envelope("[]"), MediaType.APPLICATION_JSON));

        assertThat(client.byIds(ids)).isEmpty();

        server.verify();
        assertThat(sizes).containsExactly(100, 50);
    }

    @Test
    void identityDownReturnsTheCachedSubset() {
        UUID cached = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/api/v1/users/public?ids=")))
                .andRespond(withSuccess(envelope("[" + user(cached, "carol") + "]"), MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        client.byIds(Set.of(cached));

        Map<UUID, PublicUser> result = client.byIds(Set.of(cached, missing));

        assertThat(result).containsOnlyKeys(cached);
        assertThat(failures("by_ids")).isEqualTo(1.0);
        assertThat(failures("by_username")).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
