package vn.thanhtuanle.oj.common.tls;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.server.Ssl;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * When the HTTP allowlist ({@code oj.tls.allowed-clients}) is enforced, and the rule that it may not be empty while the
 * server asks for client certificates ({@code server.ssl.client-auth}: need or want): the TLS handshake accepts any
 * certificate the CA signed (tls-probe, a worker's), so without the list every one of them would reach the API.
 * oj-common's PeerCertificateFilter (servlet) and the gateway's PeerCertificateWebFilter (WebFlux) share it.
 */
public final class AllowedClients {

    public static final String PROPERTY = "oj.tls.allowed-clients";
    static final String CLIENT_AUTH = "server.ssl.client-auth";

    private AllowedClients() {
    }

    /** The CNs to allow, without blank entries: a list of only {@code ""} or {@code " "} is no allowlist. */
    public static Set<String> of(List<String> clients) {
        return clients.stream().filter(c -> c != null && !c.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    /** The allowlist to enforce; refuses an empty one, which only happens while the server asks for certificates. */
    public static Set<String> required(OjTlsProperties properties) {
        Set<String> allowed = of(properties.allowedClients());
        if (allowed.isEmpty()) {
            throw new IllegalStateException(CLIENT_AUTH + " asks for client certificates but " + PROPERTY
                    + " is empty: every certificate the CA signed would reach the API. Set " + PROPERTY
                    + " to the CNs allowed to call this service.");
        }
        return allowed;
    }

    static boolean clientAuthRequested(Environment environment) {
        return Binder.get(environment).bind(CLIENT_AUTH, Ssl.ClientAuth.class)
                .map(clientAuth -> clientAuth != Ssl.ClientAuth.NONE).orElse(false);
    }

    /**
     * Matches when the allowlist names a client or the server asks for client certificates; the filter's bean method
     * then calls {@link #required}, so the second case without a list fails the startup. Binds the list, so comma
     * form, YAML lists and {@code [0]=} indexed properties all count.
     */
    public static class Enforced extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            List<String> clients = Binder.get(environment).bind(PROPERTY, Bindable.listOf(String.class))
                    .orElse(List.of());
            if (!of(clients).isEmpty()) {
                return ConditionOutcome.match(PROPERTY + " is set");
            }
            if (clientAuthRequested(environment)) {
                return ConditionOutcome.match(CLIENT_AUTH + " asks for client certificates");
            }
            return ConditionOutcome.noMatch(PROPERTY + " is empty and " + CLIENT_AUTH + " asks for no certificate");
        }
    }
}
