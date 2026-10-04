package vn.thanhtuanle.oj.common.web.error;

import lombok.Getter;

/** A failure with a known code: answered with the code's status and message. */
@Getter
public class AppException extends RuntimeException {
    private final ErrorCodeSpec errorCode;

    public AppException(ErrorCodeSpec errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
