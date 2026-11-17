package pl.kupony.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;

public record CouponCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9-]{3,32}$");

    public CouponCode {
        if (!isValid(value)) {
            throw new CouponException(ErrorCode.VALIDATION_FAILED,
                    "Coupon code must be 3-32 characters: letters, digits or '-'.");
        }
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    public String normalized() {
        return value.toLowerCase(Locale.ROOT);
    }
}
