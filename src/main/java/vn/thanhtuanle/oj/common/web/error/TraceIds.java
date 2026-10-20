package vn.thanhtuanle.oj.common.web.error;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

import java.util.Optional;

/** The trace id of the current request. The OTel Java agent bridges this API, so it is the trace Jaeger shows. */
public final class TraceIds {

    private TraceIds() {
    }

    /** The 32-hex trace id of the current span, or empty when there is no valid span context. */
    public static Optional<String> current() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? Optional.of(context.getTraceId()) : Optional.empty();
    }
}
