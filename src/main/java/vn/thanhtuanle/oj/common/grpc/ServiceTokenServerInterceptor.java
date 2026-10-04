package vn.thanhtuanle.oj.common.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/** Rejects, with {@code UNAUTHENTICATED}, every call that does not carry the service token. */
public class ServiceTokenServerInterceptor implements ServerInterceptor {

    private final String expected;

    /** @throws IllegalStateException when the token is missing or shorter than 32 characters */
    public ServiceTokenServerInterceptor(String token) {
        this.expected = ServiceToken.requireConfigured(token);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        if (!ServiceToken.matches(expected, headers.get(ServiceToken.HEADER))) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid service token"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        return next.startCall(call, headers);
    }
}
