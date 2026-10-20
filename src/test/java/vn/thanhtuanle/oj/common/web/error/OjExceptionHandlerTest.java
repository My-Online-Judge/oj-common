package vn.thanhtuanle.oj.common.web.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every shared handler: the HTTP status, the body's status and message, and Retry-After where it applies. */
class OjExceptionHandlerTest {

    @Getter
    @RequiredArgsConstructor
    enum TestCode implements ErrorCodeSpec {
        SLOW_DOWN("You are submitting too fast. Try again in a moment", HttpStatus.TOO_MANY_REQUESTS),
        AWAY("Problems are temporarily unavailable. Try again in a moment", HttpStatus.SERVICE_UNAVAILABLE);

        private final String message;
        private final HttpStatus statusCode;
    }

    private final OjExceptionHandler handler = new OjExceptionHandler();

    @Test
    void aCodeIsKnownByItsNameThroughTheInterface() {
        // identity-service records a failed login's reason as the code's name.
        ErrorCodeSpec code = new AppException(TestCode.AWAY).getErrorCode();

        assertThat(code.name()).isEqualTo("AWAY");
    }

    @Test
    void anAppExceptionAnswersWithItsCodesStatusAndMessage() {
        var response = handler.handleAppException(new AppException(TestCode.AWAY));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo(TestCode.AWAY.getMessage());
        assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
    }

    @Test
    void aRateLimitedExceptionCarriesItsRetryAfter() {
        var response = handler.handleRateLimitedException(new RateLimitedException(TestCode.SLOW_DOWN, 7));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("7");
        assertThat(response.getBody().getMessage()).contains("submitting too fast");
    }

    @Test
    void notFoundAlreadyExistingAndIllegalArgumentsKeepTheirStatuses() {
        assertThat(handler.handleResourceNotFoundException(new ResourceNotFoundException("Problem not found")))
                .satisfies(b -> assertThat(b.getStatus()).isEqualTo(404))
                .satisfies(b -> assertThat(b.getMessage()).isEqualTo("Problem not found"));
        assertThat(handler.handleResourceAlreadyExistException(new ResourceAlreadyExistException("slug taken")).getStatus())
                .isEqualTo(400);
        assertThat(handler.handleIllegalArgumentException(new IllegalArgumentException("bad")).getMessage())
                .isEqualTo("bad");
    }

    @Test
    void aValidationErrorListsEveryFieldAndLeadsWithOne() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "dto");
        result.addError(new FieldError("dto", "title", "Title is required"));
        var parameter = new MethodParameter(OjExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);

        var body = handler.handleValidationExceptions(new MethodArgumentNotValidException(parameter, result));

        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getMessage()).isEqualTo("Title is required");
        assertThat(body.getErrors()).containsEntry("title", "Title is required");
    }

    @Test
    void accessDeniedIs403AndAnythingElse500() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "alice", "n/a", AuthorityUtils.createAuthorityList("x")));
        assertThat(handler.handleAccessDeniedException(new AccessDeniedException("no")).getBody().getMessage())
                .isEqualTo("Access Denied: no");
        assertThat(handler.handleGlobalException(new IllegalStateException("boom")))
                .satisfies(b -> assertThat(b.getStatus()).isEqualTo(500))
                .satisfies(b -> assertThat(b.getMessage()).isEqualTo("Internal server error"));
        SecurityContextHolder.clearContext();
    }

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void catchAllHidesTheExceptionMessage() throws Exception {
        var body = handler.handleGlobalException(new IllegalStateException("password=hunter2"));

        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getMessage()).isEqualTo("Internal server error");
        assertThat(json.writeValueAsString(body)).doesNotContain("hunter2");
    }

    @Test
    void catchAllCarriesTheTraceId() {
        SpanContext sc = SpanContext.create("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7",
                TraceFlags.getSampled(), TraceState.getDefault());
        try (Scope ignored = Context.current().with(Span.wrap(sc)).makeCurrent()) {
            assertThat(handler.handleGlobalException(new IllegalStateException("x")).getTraceId())
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        }
    }

    @Test
    void catchAllWithoutTraceOmitsTraceId() throws Exception {
        var body = handler.handleGlobalException(new IllegalStateException("x"));

        assertThat(json.readTree(json.writeValueAsString(body)).has("traceId")).isFalse();
    }

    @Test
    void missingParameterIs400() {
        var body = handler.handleMissingServletRequestParameter(new MissingServletRequestParameterException("problem", "String"));

        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getMessage()).isEqualTo("Missing request parameter: problem");
    }

    @Test
    void typeMismatchIs400() {
        var body = handler.handleMethodArgumentTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Integer.class, "status", null, new NumberFormatException("abc")));

        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getMessage()).isEqualTo("Invalid value for parameter: status");
    }

    @Test
    void unknownPathIs404() throws Exception {
        var body = handler.handleNoResourceFound(new NoResourceFoundException(HttpMethod.GET, "api/v1/problems/a/b/c"));

        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getMessage()).isEqualTo("Not found");
        assertThat(json.writeValueAsString(body)).doesNotContain("static resource");
    }

    @Test
    void noHandlerIs404() {
        var body = handler.handleNoHandlerFound(new NoHandlerFoundException("GET", "/nowhere", new HttpHeaders()));

        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getMessage()).isEqualTo("Not found");
    }

    @Test
    void wrongMethodIs405() {
        var response = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST")));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().getMessage()).isEqualTo("Method not allowed");
        assertThat(response.getHeaders().getFirst("Allow")).isEqualTo("GET, POST");
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        var body = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(body.getStatus()).isEqualTo(415);
        assertThat(body.getMessage()).isEqualTo("Unsupported media type");
        assertThat(json.writeValueAsString(body)).doesNotContain("text/plain");
    }

    @Test
    void malformedJsonHidesTheParserText() throws Exception {
        var input = new ServletServerHttpRequest(new org.springframework.mock.web.MockHttpServletRequest());
        var body = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("JSON parse error: Unexpected character ('x')", input));

        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getMessage()).isEqualTo("Malformed JSON request");
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
    }
}
