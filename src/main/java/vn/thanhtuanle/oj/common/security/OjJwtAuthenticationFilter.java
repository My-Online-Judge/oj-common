package vn.thanhtuanle.oj.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Authenticates a request from an OJ access token, leniently: a missing, expired, badly signed or
 * uid-less token leaves the request anonymous instead of failing it.
 *
 * <p>Deliberately not Spring's {@code oauth2ResourceServer()}: its filter answers 401 for any
 * invalid token even on permit-all endpoints, and the 1-day access cookie keeps carrying a token
 * after it stops being usable — an old-format token after a deploy, or an expired one whenever the
 * token lifetime is configured shorter than the cookie — including on {@code /auth/refresh} and
 * {@code /auth/login}. A strict filter would lock those users out until the cookie expired.
 * Protected endpoints still answer 401 via the service's own entry point, which is what triggers
 * the portal's refresh.
 */
public class OjJwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_COOKIE = "accessToken";
    public static final String AUTHORITIES_CLAIM = "authorities";

    private final JwtDecoder decoder;

    public OjJwtAuthenticationFilter(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Jwt jwt = decoder.decode(token);
                SecurityContextHolder.getContext()
                        .setAuthentication(new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject()));
            } catch (BadJwtException e) {
                logger.debug("Ignoring unusable access token: " + e.getMessage());
            } catch (JwtException e) {
                // Not the token's fault (e.g. the JWKS endpoint is unreachable): same outcome, louder.
                logger.warn("Could not verify access token: " + e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<String> names = jwt.getClaimAsStringList(AUTHORITIES_CLAIM);
        if (names == null) {
            return List.of();
        }
        return names.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    }
}
