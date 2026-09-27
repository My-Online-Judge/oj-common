package vn.thanhtuanle.oj.common.redis;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** These strings are a contract between services (and live data in Redis): they must not drift. */
class RedisKeysTest {

    @Test
    void accessBanKeyMatchesTheExistingMirror() {
        assertThat(RedisKeys.accessBan("IP", "1.2.3.4")).isEqualTo("oj:ban:ip:1.2.3.4");
        assertThat(RedisKeys.accessBan("DEVICE", "abc")).isEqualTo("oj:ban:device:abc");
    }

    @Test
    void tokenBlocklistKeyMatchesTheExistingBlocklist() {
        assertThat(RedisKeys.tokenBlocklist("jti-1")).isEqualTo("oj:token:blocklist:jti-1");
    }

    @Test
    void revokedBeforeKeyIsPerUser() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertThat(RedisKeys.tokenRevokedBefore(id))
                .isEqualTo("oj:token:revoked-before:11111111-2222-3333-4444-555555555555");
    }
}
