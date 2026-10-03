package vn.thanhtuanle.oj.common.event;

import java.util.UUID;

/**
 * A submission reached its terminal verdict. Published once per submission (at least once on the
 * wire), keyed by {@code problemId} on {@link OjTopics#SUBMISSION_EVENTS}.
 *
 * @param verdict the submission's {@code SubmissionResult} value as stored in
 *                {@code t_submissions.status} (0 = ACCEPTED); never PENDING (6) or JUDGING (7)
 */
public record SubmissionVerdictRecorded(UUID submissionId, UUID problemId, int verdict) {

    public static final String TYPE = "SubmissionVerdictRecorded";
    public static final int VERSION = 1;

    public EventEnvelope<SubmissionVerdictRecorded> toEnvelope() {
        return EventEnvelope.of(TYPE, VERSION, this);
    }
}
