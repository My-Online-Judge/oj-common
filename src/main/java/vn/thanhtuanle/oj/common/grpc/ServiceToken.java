package vn.thanhtuanle.oj.common.grpc;

import io.grpc.Metadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The credential every internal gRPC call carries: a secret shared by the caller and the service it calls,
 * sent as the {@code x-oj-service-token} header. It authenticates services, not users — the caller has
 * already authorized its user, and some callers (a Kafka listener) have none.
 */
public final class ServiceToken {

    public static final Metadata.Key<String> HEADER =
            Metadata.Key.of("x-oj-service-token", Metadata.ASCII_STRING_MARSHALLER);

    static final int MIN_LENGTH = 32;

    private ServiceToken() {
    }

    /**
     * The configured token, stripped.
     *
     * @throws IllegalStateException when it is missing or shorter than {@value #MIN_LENGTH} characters, so a
     *                               service never starts with a guessable credential
     */
    static String requireConfigured(String token) {
        if (token == null || token.strip().length() < MIN_LENGTH) {
            throw new IllegalStateException("The service token must be a secret of at least " + MIN_LENGTH
                    + " characters (PROBLEM_RPC_TOKEN)");
        }
        return token.strip();
    }

    /** Compares in constant time: both sides are hashed to the same length first. */
    static boolean matches(String expected, String presented) {
        return presented != null && MessageDigest.isEqual(sha256(expected), sha256(presented));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JRE", e);
        }
    }
}
