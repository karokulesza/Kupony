package pl.kupony.domain.exception;

public enum ErrorCode {
    VALIDATION_FAILED(400),
    INVALID_IP_ADDRESS(400),
    COUPON_NOT_FOUND(404),
    COUPON_CODE_ALREADY_ACTIVE(409),
    COUPON_EXHAUSTED(409),
    COUPON_ALREADY_REDEEMED(409),
    COUPON_COUNTRY_MISMATCH(403),
    VPN_DETECTED(403),
    GEOLOCATION_UNAVAILABLE(503),
    COUPON_BUSY(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
