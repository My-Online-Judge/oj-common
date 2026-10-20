package vn.thanhtuanle.oj.common.grpc;

import org.junit.jupiter.api.Test;
import vn.thanhtuanle.oj.common.grpc.problem.v1.GetJudgeSpecRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GetJudgeSpecRequestTest {

    @Test
    void allowInactiveDefaultsToFalseOnTheWire() throws Exception {
        // bytes of the 0.6.0 message: only problem_slug (field 1)
        byte[] withSlug = new byte[] {0x0a, 0x01, 'a'};

        GetJudgeSpecRequest parsed = GetJudgeSpecRequest.parseFrom(withSlug);

        assertThat(parsed.getProblemSlug()).isEqualTo("a");
        assertThat(parsed.getAllowInactive()).isFalse();
    }

    @Test
    void allowInactiveRoundTrips() throws Exception {
        GetJudgeSpecRequest sent = GetJudgeSpecRequest.newBuilder().setProblemSlug("a").setAllowInactive(true).build();

        assertThat(GetJudgeSpecRequest.parseFrom(sent.toByteArray()).getAllowInactive()).isTrue();
    }
}
