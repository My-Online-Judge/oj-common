package vn.thanhtuanle.oj.common.web.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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
        assertThat(handler.handleAccessDeniedException(new AccessDeniedException("no")).getMessage())
                .isEqualTo("Access Denied: no");
        assertThat(handler.handleGlobalException(new IllegalStateException("boom")))
                .satisfies(b -> assertThat(b.getStatus()).isEqualTo(500))
                .satisfies(b -> assertThat(b.getMessage()).isEqualTo("Internal Server Error: boom"));
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
    }
}
