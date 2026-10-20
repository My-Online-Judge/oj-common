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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
        client = new PublicUserClient(builder.build(), clock, Duration.ofMinutes(5), meters);
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

    @Test
    void unknownUsernameIsEmpty() {
        server.expect(ExpectedCount.twice(), requestTo(BASE + "/api/v1/users/public/ghost"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404,\"message\":\"Not found\"}"));

        assertThat(client.byUsername("ghost")).isEmpty();
        assertThat(client.byUsername("ghost")).isEmpty(); // a 404 is not cached
        server.verify();
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
