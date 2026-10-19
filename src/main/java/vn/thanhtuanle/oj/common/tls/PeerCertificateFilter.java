package vn.thanhtuanle.oj.common.tls;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.thanhtuanle.oj.common.web.payload.ApiResponse;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.Set;

/** Answers 403 to every request whose client certificate CN is missing or not in the allowlist. */
@Slf4j
public class PeerCertificateFilter extends OncePerRequestFilter implements Ordered {

    static final String CERT_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    private final Set<String> allowed;
    private final ObjectMapper objectMapper;

    public PeerCertificateFilter(Set<String> allowed) {
        this(allowed, new ObjectMapper());
    }

    public PeerCertificateFilter(Set<String> allowed, ObjectMapper objectMapper) {
        this.allowed = Set.copyOf(allowed);
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> cn = Optional.empty();
        if (request.getAttribute(CERT_ATTRIBUTE) instanceof X509Certificate[] chainCerts && chainCerts.length > 0) {
            cn = PeerCertificates.commonName(chainCerts[0]);
        }
        if (cn.isPresent() && allowed.contains(cn.get())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rejected client certificate, CN={}", cn.orElse("<none>"));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(HttpServletResponse.SC_FORBIDDEN, "Client certificate not allowed")));
    }
}
