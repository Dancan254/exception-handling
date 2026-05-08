package com.javaguy.exceptionhandling.handler;

import com.javaguy.exceptionhandling.exception.ErrorCode;
import com.javaguy.exceptionhandling.exception.base.AppException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single global exception handler for the entire application.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} to inherit built-in handling for all
 * standard Spring MVC exceptions (400 bad request, 405 method not allowed, 415 unsupported
 * media type, etc.). Each inherited handler is overridden here to produce a consistent
 * {@link ProblemDetail} body compliant with RFC 9457 instead of Spring's default format.
 *
 * <p><strong>Handler resolution order (highest priority first):</strong>
 * <ol>
 *   <li>Most specific type — {@link AppException}, {@link DataIntegrityViolationException},
 *       {@link ResponseStatusException}</li>
 *   <li>Spring MVC internals — overridden methods from {@link ResponseEntityExceptionHandler}</li>
 *   <li>Generic fallback — {@link Exception}, which catches anything that slipped through
 *       and returns a sanitised 500 without leaking stack trace details to the client</li>
 * </ol>
 *
 * <p><strong>Every response carries three extension properties (RFC 9457 §3.2):</strong>
 * <ul>
 *   <li>{@code errorCode} — machine-readable code from {@link ErrorCode} for alert routing</li>
 *   <li>{@code traceId} — pulled from MDC if set by a request filter, otherwise a fresh UUID</li>
 *   <li>{@code timestamp} — ISO-8601 instant for client-side diagnostics</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://api.javaguy.com/errors/";

    /**
     * Handles all domain exceptions derived from {@link AppException}.
     * The HTTP status and error code are read directly from the exception instance,
     * keeping this handler decoupled from individual subtypes — adding a new exception
     * subclass requires no change here.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(AppException ex, HttpServletRequest request) {
        log.error("Application error [{}] at {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());

        ProblemDetail problem = buildProblemDetail(ex.getStatus(), ex.getMessage(), ex.getErrorCode(), request);
        problem.setType(URI.create(ERROR_TYPE_BASE + ex.getErrorCode().toLowerCase().replace('_', '-')));

        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    /**
     * Handles {@link ResponseStatusException} thrown inline from controllers.
     *
     * <p>An explicit handler is needed because the generic {@link Exception} catch-all below
     * would intercept this exception before Spring's built-in resolver could apply the
     * status code carried inside it.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(
            ResponseStatusException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        log.warn("ResponseStatusException at {}: {} — {}", request.getRequestURI(), status.value(), ex.getReason());

        String detail = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        ProblemDetail problem = buildProblemDetail(status, detail, ErrorCode.RESPONSE_STATUS_ERROR, request);

        return ResponseEntity.status(status).body(problem);
    }

    /**
     * Handles database-level unique constraint violations that were not caught earlier
     * by a service-layer existence check.
     *
     * <p>The internal constraint name from {@link DataIntegrityViolationException#getMostSpecificCause()}
     * is intentionally not exposed to the client — it leaks schema details. It is logged
     * for debugging and replaced with a generic message in the response.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.error("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.CONFLICT,
            "A resource with the same unique value already exists.",
            ErrorCode.DATA_CONFLICT,
            request
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Overrides the default Spring MVC handling of Bean Validation failures to enrich
     * the response with a field-level error map. This makes the response actionable:
     * clients know exactly which fields failed and why, without parsing a free-text message.
     *
     * <p>When the same field has multiple violations, their messages are concatenated
     * with "; " so no information is silently dropped.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.warn("Validation failure for request {}: {} field error(s)",
            request.getDescription(false), ex.getBindingResult().getErrorCount());

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                (first, second) -> first + "; " + second
            ));

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more request fields failed validation.");
        problem.setType(URI.create(ERROR_TYPE_BASE + "validation-failed"));
        problem.setProperty("errors", fieldErrors);
        problem.setProperty("traceId", resolveTraceId());
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    /**
     * Last-resort handler for any exception not caught by the handlers above.
     * Logs the full stack trace at ERROR level for operational visibility while
     * returning a generic 500 message that deliberately avoids leaking internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.",
            ErrorCode.INTERNAL_ERROR,
            request
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    /**
     * Constructs a {@link ProblemDetail} pre-populated with the standard extension
     * properties shared across all error responses in this application.
     */
    private ProblemDetail buildProblemDetail(
            HttpStatus status, String detail, String errorCode, HttpServletRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("traceId", resolveTraceId());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Returns the trace ID set in MDC by an upstream filter (e.g., a gateway or tracing
     * agent), falling back to a locally generated UUID when none is present.
     */
    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
