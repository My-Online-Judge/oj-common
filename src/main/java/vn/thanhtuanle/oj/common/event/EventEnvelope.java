package vn.thanhtuanle.oj.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Wrapper for every OJ domain event on Kafka. The judge-worker topics ({@code submission.requested},
 * {@code submission.judged}) keep their own historical format and do not use it.
 *
 * @param eventId    unique per event; consumers may use it for logging, never for deduplication —
 *                   a payload's natural key (e.g. the submission id) is the idempotency key
 * @param eventType  the payload's {@code TYPE}
 * @param version    the payload's schema version; bump it on any incompatible payload change
 * @param occurredAt when the producer recorded the fact
 */
public record EventEnvelope<T>(UUID eventId, String eventType, int version, Instant occurredAt, T payload) {

    public static <T> EventEnvelope<T> of(String eventType, int version, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, version, Instant.now(), payload);
    }
}
