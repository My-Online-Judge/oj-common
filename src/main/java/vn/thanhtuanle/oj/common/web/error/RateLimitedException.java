package vn.thanhtuanle.oj.common.web.error;

import lombok.Getter;

/** An AppException that knows how long the caller must wait — mapped to a dynamic Retry-After. */
@Getter
public class RateLimitedException extends AppException {

    private final long retryAfterSeconds;

    public RateLimitedException(ErrorCodeSpec errorCode, long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
