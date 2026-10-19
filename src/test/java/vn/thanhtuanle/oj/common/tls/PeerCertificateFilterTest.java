package vn.thanhtuanle.oj.common.tls;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.cert.X509Certificate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PeerCertificateFilterTest {

    private final PeerCertificateFilter filter = new PeerCertificateFilter(Set.of("edge"));
    private final FilterChain chain = mock(FilterChain.class);

    private MockHttpServletRequest requestWith(String cert) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (cert != null) {
            request.setAttribute("jakarta.servlet.request.X509Certificate",
                    new X509Certificate[] { Fixtures.cert(cert) });
        }
        return request;
    }

    @Test
    void allowedCnPasses() throws Exception {
        MockHttpServletRequest request = requestWith("edge");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void otherCnGets403Json() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWith("tls-probe"), response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        com.fasterxml.jackson.databind.JsonNode body =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("message").asText()).isEqualTo("Client certificate not allowed");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void noCertificateGets403() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWith(null), response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
