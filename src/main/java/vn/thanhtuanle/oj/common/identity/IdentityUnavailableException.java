package vn.thanhtuanle.oj.common.identity;

/** Identity could not answer (network, timeout, 5xx, unreadable body). Not a 404: that is an empty result. */
public class IdentityUnavailableException extends RuntimeException {

    public IdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
