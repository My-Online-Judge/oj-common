package vn.thanhtuanle.oj.common.grpc;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import vn.thanhtuanle.oj.common.tls.PeerCertificates;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.Set;

/** Rejects, with {@code PERMISSION_DENIED}, every call whose client certificate CN is missing or not allowed. */
@Slf4j
public class PeerIdentityServerInterceptor implements ServerInterceptor {

    private final Set<String> allowed;

    public PeerIdentityServerInterceptor(Set<String> allowed) {
        this.allowed = Set.copyOf(allowed);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        Optional<String> cn = peerCommonName(call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION));
        if (cn.isEmpty() || !allowed.contains(cn.get())) {
            log.warn("Rejected gRPC peer, CN={}", cn.orElse("<none>"));
            call.close(Status.PERMISSION_DENIED.withDescription("peer not allowed"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        return next.startCall(call, headers);
    }

    private static Optional<String> peerCommonName(SSLSession session) {
        if (session == null) {
            return Optional.empty();
        }
        try {
            Certificate[] peers = session.getPeerCertificates();
            if (peers.length > 0 && peers[0] instanceof X509Certificate leaf) {
                return PeerCertificates.commonName(leaf);
            }
        } catch (SSLPeerUnverifiedException e) {
            // no client certificate
        }
        return Optional.empty();
    }
}
