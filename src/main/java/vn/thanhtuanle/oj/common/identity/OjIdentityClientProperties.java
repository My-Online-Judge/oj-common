package vn.thanhtuanle.oj.common.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param baseUrl   identity's base URL, e.g. {@code https://identity-service:8000}; the client exists only when set
 * @param sslBundle optional Spring SSL bundle name; when set, identity is called with that bundle's client
 *                  certificate and trust (mTLS)
 * @param cacheTtl  how long a fetched profile is served from memory
 * @param negativeCacheTtl how long an unknown username or id is remembered as absent
 */
@ConfigurationProperties("oj.identity")
public record OjIdentityClientProperties(String baseUrl, String sslBundle,
                                         @DefaultValue("5m") Duration cacheTtl,
                                         @DefaultValue("60s") Duration negativeCacheTtl) {
}
