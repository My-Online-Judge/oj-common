package vn.thanhtuanle.oj.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * What every service's own {@code ErrorCode} enum provides: the message users see and the HTTP status it is
 * answered with. Each service keeps its own enum (and its codes), so adding a code never touches oj-common.
 */
public interface ErrorCodeSpec {

    /** The code's identifier (e.g. {@code RATE_LIMITED}); an enum provides it. */
    String name();

    String getMessage();

    HttpStatus getStatusCode();
}
