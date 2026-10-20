package vn.thanhtuanle.oj.common.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wire format is a contract between the submission side (producer) and problem-service
 * (consumer), which are built and deployed separately: these field names must not drift.
 */
class SubmissionVerdictRecordedTest {

    // Same settings Spring Boot gives its auto-configured ObjectMapper.
    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID SUBMISSION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PROBLEM = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CONTEST = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant SUBMITTED = Instant.parse("2026-10-03T09:59:00Z");

    @Test
    void serializesToThePinnedWireFormat() throws Exception {
        EventEnvelope<SubmissionVerdictRecorded> envelope = new EventEnvelope<>(EVENT,
                SubmissionVerdictRecorded.TYPE, SubmissionVerdictRecorded.VERSION,
                Instant.parse("2026-10-03T10:00:00Z"), new SubmissionVerdictRecorded(SUBMISSION, PROBLEM, 0, false, USER, CONTEST, SUBMITTED));

        assertThat(mapper.writeValueAsString(envelope)).isEqualTo("{"
                + "\"eventId\":\"00000000-0000-0000-0000-00000000000e\","
                + "\"eventType\":\"SubmissionVerdictRecorded\","
                + "\"version\":1,"
                + "\"occurredAt\":\"2026-10-03T10:00:00Z\","
                + "\"payload\":{\"submissionId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"problemId\":\"00000000-0000-0000-0000-000000000002\",\"verdict\":0,\"rejudge\":false,"
                + "\"userId\":\"00000000-0000-0000-0000-000000000003\","
                + "\"contestId\":\"00000000-0000-0000-0000-000000000004\","
                + "\"submittedAt\":\"2026-10-03T09:59:00Z\"}}");
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        EventEnvelope<SubmissionVerdictRecorded> sent = new SubmissionVerdictRecorded(SUBMISSION, PROBLEM, -1,
                true, USER, CONTEST, SUBMITTED).toEnvelope();

        EventEnvelope<SubmissionVerdictRecorded> received = mapper.readValue(mapper.writeValueAsBytes(sent),
                new TypeReference<EventEnvelope<SubmissionVerdictRecorded>>() { });

        assertThat(received).isEqualTo(sent);
        assertThat(received.eventType()).isEqualTo("SubmissionVerdictRecorded");
        assertThat(received.version()).isEqualTo(1);
    }

    @Test
    void aRejudgeRoundTrips() throws Exception {
        EventEnvelope<SubmissionVerdictRecorded> sent = new SubmissionVerdictRecorded(SUBMISSION, PROBLEM, -1, true)
                .toEnvelope();

        EventEnvelope<SubmissionVerdictRecorded> received = mapper.readValue(mapper.writeValueAsBytes(sent),
                new TypeReference<EventEnvelope<SubmissionVerdictRecorded>>() { });

        assertThat(received).isEqualTo(sent);
        assertThat(received.payload().rejudge()).isTrue();
    }

    @Test
    void anEventFromAnOlderProducerReadsAsFirstVerdict() throws Exception {
        String old = "{\"eventId\":\"00000000-0000-0000-0000-00000000000e\","
                + "\"eventType\":\"SubmissionVerdictRecorded\",\"version\":1,"
                + "\"occurredAt\":\"2026-10-03T10:00:00Z\","
                + "\"payload\":{\"submissionId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"problemId\":\"00000000-0000-0000-0000-000000000002\",\"verdict\":0}}";

        EventEnvelope<SubmissionVerdictRecorded> received = mapper.readValue(old,
                new TypeReference<EventEnvelope<SubmissionVerdictRecorded>>() { });

        assertThat(received.payload().rejudge()).isFalse();
        assertThat(received.payload()).isEqualTo(new SubmissionVerdictRecorded(SUBMISSION, PROBLEM, 0));
    }

    @Test
    void readsAPayloadWithoutTheNewFields() throws Exception {
        String old = "{\"eventId\":\"00000000-0000-0000-0000-00000000000e\","
                + "\"eventType\":\"SubmissionVerdictRecorded\",\"version\":1,"
                + "\"occurredAt\":\"2026-10-03T10:00:00Z\","
                + "\"payload\":{\"submissionId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"problemId\":\"00000000-0000-0000-0000-000000000002\",\"verdict\":-1}}";

        SubmissionVerdictRecorded p = mapper.readValue(old,
                new TypeReference<EventEnvelope<SubmissionVerdictRecorded>>() { }).payload();

        assertThat(p.userId()).isNull();
        assertThat(p.contestId()).isNull();
        assertThat(p.submittedAt()).isNull();
        assertThat(p.rejudge()).isFalse();
    }
}
