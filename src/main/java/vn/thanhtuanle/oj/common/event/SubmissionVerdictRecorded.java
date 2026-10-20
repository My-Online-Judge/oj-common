package vn.thanhtuanle.oj.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A submission reached its terminal verdict. Published once per submission (at least once on the
 * wire), keyed by {@code problemId} on {@link OjTopics#SUBMISSION_EVENTS}.
 *
 * @param verdict the submission's {@code SubmissionResult} value as stored in
 *                {@code t_submissions.status} (0 = ACCEPTED); never PENDING (6) or JUDGING (7)
 * @param rejudge true when this verdict replaces an earlier one (a rejudge); absent on the wire from
 *                producers older than 0.6.0, which reads as false
 * @param userId the submitter; null only from producers older than 0.8.0
 * @param contestId the contest the submission was made in; null for practice
 * @param submittedAt when the submission was made; null only from producers older than 0.8.0
 */
public record SubmissionVerdictRecorded(UUID submissionId, UUID problemId, int verdict, boolean rejudge,
                                        UUID userId, UUID contestId, Instant submittedAt) {

    public static final String TYPE = "SubmissionVerdictRecorded";
    public static final int VERSION = 1;

    /** A first verdict (not a rejudge), without submitter or contest. */
    public SubmissionVerdictRecorded(UUID submissionId, UUID problemId, int verdict) {
        this(submissionId, problemId, verdict, false);
    }

    /** Without submitter or contest. */
    public SubmissionVerdictRecorded(UUID submissionId, UUID problemId, int verdict, boolean rejudge) {
        this(submissionId, problemId, verdict, rejudge, null, null, null);
    }

    public EventEnvelope<SubmissionVerdictRecorded> toEnvelope() {
        return EventEnvelope.of(TYPE, VERSION, this);
    }
}
