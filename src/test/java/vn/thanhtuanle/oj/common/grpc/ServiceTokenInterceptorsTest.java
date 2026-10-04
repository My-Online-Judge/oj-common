package vn.thanhtuanle.oj.common.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.thanhtuanle.oj.common.grpc.problem.v1.GetJudgeSpecRequest;
import vn.thanhtuanle.oj.common.grpc.problem.v1.JudgeSpec;
import vn.thanhtuanle.oj.common.grpc.problem.v1.ProblemInternalGrpc;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTokenInterceptorsTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void startServer() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(new EchoProblems(), new ServiceTokenServerInterceptor(TOKEN)))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void stopServer() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void aCallCarryingTheTokenGoesThrough() {
        JudgeSpec spec = ProblemInternalGrpc.newBlockingStub(channel)
                .withInterceptors(new ServiceTokenClientInterceptor(TOKEN))
                .getJudgeSpec(GetJudgeSpecRequest.newBuilder().setProblemSlug("a-plus-b").build());

        assertThat(spec.getProblemSlug()).isEqualTo("a-plus-b");
    }

    @Test
    void aCallWithoutTheTokenIsUnauthenticated() {
        var stub = ProblemInternalGrpc.newBlockingStub(channel);

        assertThatThrownBy(() -> stub.getJudgeSpec(GetJudgeSpecRequest.newBuilder().setProblemSlug("x").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void aCallWithAnotherTokenIsUnauthenticated() {
        var stub = ProblemInternalGrpc.newBlockingStub(channel)
                .withInterceptors(new ServiceTokenClientInterceptor("ffffffffffffffffffffffffffffffff"));

        assertThatThrownBy(() -> stub.getJudgeSpec(GetJudgeSpecRequest.newBuilder().setProblemSlug("x").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void aTokenShorterThan32CharactersIsRefusedAtStartup() {
        assertThatThrownBy(() -> new ServiceTokenServerInterceptor("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
        assertThatThrownBy(() -> new ServiceTokenClientInterceptor(null))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Answers every GetJudgeSpec with the requested slug. */
    private static final class EchoProblems extends ProblemInternalGrpc.ProblemInternalImplBase {
        @Override
        public void getJudgeSpec(GetJudgeSpecRequest request, StreamObserver<JudgeSpec> responseObserver) {
            responseObserver.onNext(JudgeSpec.newBuilder().setProblemSlug(request.getProblemSlug()).build());
            responseObserver.onCompleted();
        }
    }
}
