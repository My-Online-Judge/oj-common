package vn.thanhtuanle.oj.common.web.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the shared handler logs: a client error is one line without a stack trace, a server error keeps its stack.
 */
class OjExceptionHandlerLoggingTest {

    @Getter
    @RequiredArgsConstructor
    enum TestCode implements ErrorCodeSpec {
        TEST_CONFLICT("Conflict", HttpStatus.CONFLICT),
        TEST_DOWN("Down", HttpStatus.SERVICE_UNAVAILABLE),
        TEST_SLOW("Slow", HttpStatus.TOO_MANY_REQUESTS),
        TEST_BROKEN("Broken", HttpStatus.INTERNAL_SERVER_ERROR);

        private final String message;
        private final HttpStatus statusCode;
    }

    private final OjExceptionHandler handler = new OjExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(OjExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void attach() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        SecurityContextHolder.clearContext();
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    void aNotFoundIsLoggedAtDebugWithoutAStack() {
        handler.handleResourceNotFoundException(new ResourceNotFoundException("Problem not found"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(event.getFormattedMessage()).isEqualTo("Not found: Problem not found");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void aClientAppExceptionIsOneWarnLine() {
        handler.handleAppException(new AppException(TestCode.TEST_CONFLICT));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("TEST_CONFLICT (409): Conflict");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void aServerAppExceptionKeepsItsStackAtError() {
        handler.handleAppException(new AppException(TestCode.TEST_BROKEN));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    void aRateLimited503IsOneWarnLine_a429IsDebug() {
        handler.handleRateLimitedException(new RateLimitedException(TestCode.TEST_DOWN, 30));

        ILoggingEvent down = onlyEvent();
        assertThat(down.getLevel()).isEqualTo(Level.WARN);
        assertThat(down.getFormattedMessage()).isEqualTo("TEST_DOWN (503), Retry-After 30s");
        assertThat(down.getThrowableProxy()).isNull();

        appender.list.clear();
        handler.handleRateLimitedException(new RateLimitedException(TestCode.TEST_SLOW, 7));

        ILoggingEvent slow = onlyEvent();
        assertThat(slow.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(slow.getThrowableProxy()).isNull();
    }

    @Test
    void anIllegalArgumentWithItsOwnMessageIsOneWarnLine() {
        handler.handleIllegalArgumentException(new IllegalArgumentException("Unsupported language: java"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("Bad request: Unsupported language: java");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void aWithheldIllegalArgumentKeepsItsStackAtWarn() {
        handler.handleIllegalArgumentException(new IllegalArgumentException("Invalid UUID string: secret-input"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    void aValidationFailureIsOneWarnLine() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "dto");
        result.addError(new FieldError("dto", "title", "Title is required"));
        var parameter = new MethodParameter(
                OjExceptionHandlerLoggingTest.class.getDeclaredMethod("sample", String.class), 0);

        handler.handleValidationExceptions(new MethodArgumentNotValidException(parameter, result));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("Validation failed: [title]");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void alreadyExistsIsOneWarnLine() {
        handler.handleResourceAlreadyExistException(new ResourceAlreadyExistException("slug taken"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("Already exists: slug taken");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void aSignedInAccessDeniedIsOneWarnLine() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "alice", "n/a", AuthorityUtils.createAuthorityList("x")));

        handler.handleAccessDeniedException(new AccessDeniedException("no"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("Access denied: no");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void theCatchAllStaysErrorWithItsStack() {
        handler.handleGlobalException(new IllegalStateException("boom"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
    }
}
