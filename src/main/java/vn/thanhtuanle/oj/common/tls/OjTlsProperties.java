package vn.thanhtuanle.oj.common.tls;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Client-certificate CNs allowed to call this service over HTTP and over gRPC. */
@ConfigurationProperties("oj.tls")
public record OjTlsProperties(List<String> allowedClients, List<String> grpcAllowedClients) {

    public OjTlsProperties {
        allowedClients = allowedClients == null ? List.of() : List.copyOf(allowedClients);
        grpcAllowedClients = grpcAllowedClients == null ? List.of() : List.copyOf(grpcAllowedClients);
    }
}
