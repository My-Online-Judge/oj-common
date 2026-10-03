package vn.thanhtuanle.oj.common.event;

/** Kafka topics that carry {@link EventEnvelope}s between OJ services. */
public final class OjTopics {

    /** Facts about submissions, produced by the submission side. Key: the problem id. */
    public static final String SUBMISSION_EVENTS = "oj.submission.events";

    /** Records from {@link #SUBMISSION_EVENTS} that a consumer could not process. */
    public static final String SUBMISSION_EVENTS_DLQ = "oj.submission.events.dlq";

    private OjTopics() {
    }
}
