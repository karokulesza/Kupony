package pl.kupony.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CouponException.class)
    ResponseEntity<ProblemDetail> handleCoupon(CouponException e, HttpServletRequest request) {
        var response = ResponseEntity.status(e.code().httpStatus());
        e.retryAfter().ifPresent(d -> response.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, (d.toMillis() + 999) / 1000))));
        return response.body(problem(e.code(), e.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "Request validation failed.", request);
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleUnreadable(Exception e, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(ErrorCode.VALIDATION_FAILED, "Malformed request.", request));
    }

    private static ProblemDetail problem(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatusCode.valueOf(code.httpStatus()), detail);
        body.setTitle(title(code));
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("errorCode", code.name());
        return body;
    }

    private static String title(ErrorCode code) {
        String words = code.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
