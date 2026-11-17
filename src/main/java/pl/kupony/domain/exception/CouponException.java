package pl.kupony.domain.exception;

import java.time.Duration;
import java.util.Optional;

public class CouponException extends RuntimeException {

    private final ErrorCode code;
    private final transient Duration retryAfter;

    public CouponException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public CouponException(ErrorCode code, String message, Duration retryAfter) {
        super(message);
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public ErrorCode code() {
        return code;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
