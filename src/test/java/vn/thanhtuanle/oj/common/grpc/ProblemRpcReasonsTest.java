package vn.thanhtuanle.oj.common.grpc;

import io.grpc.Status;
import org.junit.jupiter.api.Test;
import vn.thanhtuanle.oj.common.grpc.problem.v1.GetJudgeSpecRequest;
import vn.thanhtuanle.oj.common.grpc.problem.v1.JudgeSpec;
import vn.thanhtuanle.oj.common.grpc.problem.v1.ListVisibleSlugsRequest;
import vn.thanhtuanle.oj.common.grpc.problem.v1.ListVisibleSlugsResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemRpcReasonsTest {

    @Test
    void contestNotRunningIsReadBackFromTrailers() {
        assertThat(ProblemRpcReasons.isContestNotRunning(Status.FAILED_PRECONDITION
                .withDescription("Contest is not running")
                .asRuntimeException(ProblemRpcReasons.contestNotRunning()))).isTrue();
        assertThat(ProblemRpcReasons.isContestNotRunning(
                Status.FAILED_PRECONDITION.asRuntimeException())).isFalse();
        assertThat(ProblemRpcReasons.isContestNotRunning(
                Status.NOT_FOUND.asRuntimeException(ProblemRpcReasons.contestNotRunning()))).isFalse();
    }

    @Test
    void newProtoFieldsDefaultToEmpty() {
        assertThat(GetJudgeSpecRequest.newBuilder().setProblemSlug("a").build().getContestSlug()).isEmpty();
        assertThat(JudgeSpec.getDefaultInstance().getContestId()).isEmpty();
        assertThat(JudgeSpec.getDefaultInstance().getSpjSource()).isEmpty();
        assertThat(JudgeSpec.getDefaultInstance().getSpjVersion()).isEmpty();
    }

    @Test
    void newProtoFieldsRoundTrip() throws Exception {
        GetJudgeSpecRequest req = GetJudgeSpecRequest.newBuilder().setProblemSlug("a").setContestSlug("c1").build();
        assertThat(GetJudgeSpecRequest.parseFrom(req.toByteArray()).getContestSlug()).isEqualTo("c1");

        JudgeSpec spec = JudgeSpec.newBuilder().setContestId("id").setSpjSource("int main(){}")
                .setSpjVersion("abc").build();
        JudgeSpec back = JudgeSpec.parseFrom(spec.toByteArray());
        assertThat(back.getContestId()).isEqualTo("id");
        assertThat(back.getSpjSource()).isEqualTo("int main(){}");
        assertThat(back.getSpjVersion()).isEqualTo("abc");
    }

    @Test
    void listVisibleSlugsRoundTrips() throws Exception {
        ListVisibleSlugsRequest req = ListVisibleSlugsRequest.newBuilder().addSlugs("a").addSlugs("b").build();
        assertThat(ListVisibleSlugsRequest.parseFrom(req.toByteArray()).getSlugsList()).containsExactly("a", "b");
        assertThat(ListVisibleSlugsRequest.getDefaultInstance().getSlugsList()).isEmpty();

        ListVisibleSlugsResponse res = ListVisibleSlugsResponse.newBuilder().addSlugs("a").build();
        assertThat(ListVisibleSlugsResponse.parseFrom(res.toByteArray()).getSlugsList()).containsExactly("a");
    }

    @Test
    void notEnrolledIsReadBackFromTrailers() {
        // The Run code / contest enrollment design §4.4: a second FAILED_PRECONDITION reason, told apart from the first.
        assertThat(ProblemRpcReasons.isNotEnrolled(Status.FAILED_PRECONDITION
                .withDescription("Not enrolled in contest")
                .asRuntimeException(ProblemRpcReasons.notEnrolled()))).isTrue();
        assertThat(ProblemRpcReasons.isNotEnrolled(
                Status.FAILED_PRECONDITION.asRuntimeException(ProblemRpcReasons.contestNotRunning()))).isFalse();
        assertThat(ProblemRpcReasons.isContestNotRunning(
                Status.FAILED_PRECONDITION.asRuntimeException(ProblemRpcReasons.notEnrolled()))).isFalse();
        assertThat(ProblemRpcReasons.isNotEnrolled(
                Status.NOT_FOUND.asRuntimeException(ProblemRpcReasons.notEnrolled()))).isFalse();
    }
}
