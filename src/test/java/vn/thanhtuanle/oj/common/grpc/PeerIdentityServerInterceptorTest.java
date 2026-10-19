package vn.thanhtuanle.oj.common.grpc;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({ "unchecked", "rawtypes" })
class PeerIdentityServerInterceptorTest {

    private final PeerIdentityServerInterceptor interceptor =
            new PeerIdentityServerInterceptor(Set.of("submission-service"));
    private final ServerCallHandler next = mock(ServerCallHandler.class);
    private final ServerCall call = mock(ServerCall.class);

    private void attach(SSLSession session) {
        Attributes.Builder attrs = Attributes.newBuilder();
        if (session != null) {
            attrs.set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session);
        }
        when(call.getAttributes()).thenReturn(attrs.build());
    }

    private SSLSession sessionOf(String name) throws Exception {
        SSLSession session = mock(SSLSession.class);
        Certificate cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(getClass().getResourceAsStream("/tls/" + name + ".crt"));
        when(session.getPeerCertificates()).thenReturn(new Certificate[] { cert });
        return session;
    }

    private void assertDenied() {
        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(call).close(status.capture(), any(Metadata.class));
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(status.getValue().getDescription()).isEqualTo("peer not allowed");
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void allowedPeerProceeds() throws Exception {
        attach(sessionOf("submission-service"));
        interceptor.interceptCall(call, new Metadata(), next);
        verify(next).startCall(org.mockito.ArgumentMatchers.eq(call), any(Metadata.class));
        verify(call, never()).close(any(), any());
    }

    @Test
    void otherPeerDenied() throws Exception {
        attach(sessionOf("api-gateway"));
        interceptor.interceptCall(call, new Metadata(), next);
        assertDenied();
    }

    @Test
    void noSslSessionDenied() {
        attach(null);
        interceptor.interceptCall(call, new Metadata(), next);
        assertDenied();
    }

    @Test
    void peerWithoutCnDenied() throws Exception {
        attach(sessionOf("no-cn"));
        interceptor.interceptCall(call, new Metadata(), next);
        assertDenied();
    }
}
