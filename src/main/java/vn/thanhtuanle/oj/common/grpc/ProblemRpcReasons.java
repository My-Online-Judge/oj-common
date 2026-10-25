package vn.thanhtuanle.oj.common.grpc;

import io.grpc.Metadata;
import io.grpc.Status;

/**
 * Machine-readable reasons problem-service attaches to a gRPC failure as the {@code oj-reason} trailer, for
 * callers that must tell apart two failures with the same status code.
 */
public final class ProblemRpcReasons {

    public static final Metadata.Key<String> REASON =
            Metadata.Key.of("oj-reason", Metadata.ASCII_STRING_MARSHALLER);

    /** FAILED_PRECONDITION: the named contest is not running, or does not contain the problem. */
    public static final String CONTEST_NOT_RUNNING = "CONTEST_NOT_RUNNING";
    /**
     * FAILED_PRECONDITION: the named contest requires registration and the submitter ({@code user_id}) is not
     * registered, or the request names no submitter (contest enrollment design §4.4).
     */
    public static final String NOT_ENROLLED = "NOT_ENROLLED";

    private ProblemRpcReasons() {
    }

    /** Trailers for a {@link #CONTEST_NOT_RUNNING} failure. */
    public static Metadata contestNotRunning() {
        Metadata trailers = new Metadata();
        trailers.put(REASON, CONTEST_NOT_RUNNING);
        return trailers;
    }

    /** True iff {@code e} is a FAILED_PRECONDITION whose trailers carry {@link #CONTEST_NOT_RUNNING}. */
    public static boolean isContestNotRunning(Throwable e) {
        return hasReason(e, CONTEST_NOT_RUNNING);
    }

    /** Trailers for a {@link #NOT_ENROLLED} failure. */
    public static Metadata notEnrolled() {
        Metadata trailers = new Metadata();
        trailers.put(REASON, NOT_ENROLLED);
        return trailers;
    }

    /** True iff {@code e} is a FAILED_PRECONDITION whose trailers carry {@link #NOT_ENROLLED}. */
    public static boolean isNotEnrolled(Throwable e) {
        return hasReason(e, NOT_ENROLLED);
    }

    private static boolean hasReason(Throwable e, String reason) {
        if (Status.fromThrowable(e).getCode() != Status.Code.FAILED_PRECONDITION) {
            return false;
        }
        Metadata trailers = Status.trailersFromThrowable(e);
        return trailers != null && reason.equals(trailers.get(REASON));
    }
}
