package vn.thanhtuanle.oj.common.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/** Adds the service token to every outgoing call. */
public class ServiceTokenClientInterceptor implements ClientInterceptor {

    private final String token;

    /** @throws IllegalStateException when the token is missing or shorter than 32 characters */
    public ServiceTokenClientInterceptor(String token) {
        this.token = ServiceToken.requireConfigured(token);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(ServiceToken.HEADER, token);
                super.start(responseListener, headers);
            }
        };
    }
}
