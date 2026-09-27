package vn.thanhtuanle.oj.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/** The user behind the current request, read from its access token — no database lookup. */
public class CurrentUser {

    public Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new IllegalStateException("No authenticated OJ access token on this request");
    }

    public UUID id() {
        return UUID.fromString(jwt().getClaimAsString(OjJwtDecoders.UID_CLAIM));
    }
}
