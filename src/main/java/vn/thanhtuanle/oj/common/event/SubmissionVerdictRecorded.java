package vn.thanhtuanle.oj.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A submission reached its terminal verdict. Published once per judging of a submission (at least once on the
 * wire), keyed by {@code problemId} on {@link OjTopics#SUBMISSION_EVENTS}.
 *
 * @param verdict the submission's {@code SubmissionResult} value as stored in
 *                {@code t_submissions.status} (0 = ACCEPTED); never PENDING (6) or JUDGING (7)
 * @param rejudge true when this verdict replaces an earlier one (a rejudge); absent on the wire from
 *                producers older than 0.6.0, which reads as false
 * @param userId the submitter; null only from producers older than 0.8.0
 * @param contestId the contest the submission was made in; null for practice
 * @param submittedAt when the submission was made; null only from producers older than 0.8.0
 * @param rejudgeCount the generation this verdict belongs to: the submission's {@code rejudge_count} when it was
 *                     judged, 0 for the first verdict and n after the n-th rejudge. Two verdicts of one submission can
 *                     reach a consumer in either order (two outbox relay passes, a dead letter replayed), so a consumer
 *                     ignores a verdict of a generation older than one it already applied. Null only from producers
 *                     older than 0.10.0: such a verdict cannot be ordered and is applied as it arrives.
 */
public record SubmissionVerdictRecorded(UUID submissionId, UUID problemId, int verdict, boolean rejudge,
                                        UUID userId, UUID contestId, Instant submittedAt, Integer rejudgeCount) {

    public static final String TYPE = "SubmissionVerdictRecorded";
    public static final int VERSION = 1;

    /** A first verdict (not a rejudge), without submitter, contest or generation. */
    public SubmissionVerdictRecorded(UUID submissionId, UUID problemId, int verdict) {
        this(submissionId, problemId, verdict, false);
    }

    /** Without submitter, contest or generation. */
    public SubmissionVerdictRecorded(UUID submissionId, UUID problemId, int verdict, boolean rejudge) {
        this(submissionId, problemId, verdict, rejudge, null, null, null);
    }

    /** Without generation, as producers older than 0.10.0 send it. */
    public SubmissionVerdictRecorded(UUID submissionId, UUID problemId, int verdict, boolean rejudge,
                                     UUID userId, UUID contestId, Instant submittedAt) {
        this(submissionId, problemId, verdict, rejudge, userId, contestId, submittedAt, null);
    }

    public EventEnvelope<SubmissionVerdictRecorded> toEnvelope() {
        return EventEnvelope.of(TYPE, VERSION, this);
    }
}
