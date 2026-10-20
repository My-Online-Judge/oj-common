package vn.thanhtuanle.oj.common.web.error;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdsTest {

    @Test
    void noActiveSpanIsEmpty() {
        assertThat(TraceIds.current()).isEmpty();
    }

    @Test
    void anActiveSpanGivesItsTraceId() {
        SpanContext sc = SpanContext.create("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7",
                TraceFlags.getSampled(), TraceState.getDefault());
        try (Scope ignored = Context.current().with(Span.wrap(sc)).makeCurrent()) {
            assertThat(TraceIds.current()).contains("4bf92f3577b34da6a3ce929d0e0e4736");
        }
    }
}
