package dev.duynguyen.jobtracker.common;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolationException;

/**
 * Every error the API returns, in one place, as RFC 7807 {@code ProblemDetail}
 * (CLAUDE.md §11).
 *
 * <p>One advice rather than per-controller handling, and <strong>not</strong> extending
 * {@code ResponseEntityExceptionHandler}: that base class already handles several of these
 * and would quietly win over the methods here for anything not overridden with the exact
 * signature it expects. A plain advice makes the mapping explicit and greppable — the list
 * of {@code @ExceptionHandler} annotations below <em>is</em> the API's error contract.
 *
 * <p>Spring serializes {@code ProblemDetail} as
 * {@code {type, title, status, detail, instance}} with {@code Content-Type:
 * application/problem+json}. Field-level validation errors are attached as an extra
 * {@code errors} property.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MeterRegistry metrics;

    GlobalExceptionHandler(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    // ------------------------------------------------- our own exceptions

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    // ------------------------------------------------------- bean validation

    /**
     * Bean Validation failures on an {@code @Valid @RequestBody}.
     *
     * <p>Returns every violation, not the first. A form that fails one field at a time
     * across four round trips is a bad experience, and the SPA's forms map these straight
     * onto their fields.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of(
                        "field", e.getField(),
                        "message", e.getDefaultMessage() == null ? "is invalid" : e.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Violations on {@code @Validated} method parameters, e.g. a constrained query param. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "One or more parameters are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    // ------------------------------------------------------------ binding

    /**
     * Malformed JSON, or a value Jackson cannot turn into the target type — most often an
     * unknown enum constant.
     *
     * <p><strong>Jackson's own message is passed through deliberately.</strong> For a bad
     * enum it reads "not one of the values accepted for Enum class: [ACTIVE, OFFER, ...]",
     * which names every legal value — exactly what a caller needs, and what the MCP server
     * and the {@code .http} collection surface while you are working. The trade-off is that
     * it exposes DTO type names; for a single-user API whose whole schema is public in
     * {@code SCHEMA.md} that costs nothing.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String detail = cause.getMessage() == null
                ? "Request body could not be read"
                : cause.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "Malformed Request", trim(detail));
    }

    /** A query param that will not convert — {@code ?days=soon}, {@code ?status=BOGUS}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() == null ? "the expected type"
                : ex.getRequiredType().getSimpleName();
        return problem(HttpStatus.BAD_REQUEST, "Bad Request",
                "Parameter '%s' cannot be converted to %s: '%s'"
                        .formatted(ex.getName(), expected, ex.getValue()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request",
                "Required parameter '%s' is missing".formatted(ex.getParameterName()));
    }

    // ------------------------------------------------------------ fallback

    /**
     * The catch-all. Logs the stack trace and returns nothing about it.
     *
     * <p>Without this an unexpected exception escapes as Spring's default error body, which
     * varies by configuration. This guarantees a 500 is always the same shape as every other
     * error — and, once Phase 5 wires up metrics, always countable in one place.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Something went wrong. Check the server logs.");
    }

    // ------------------------------------------------------------- helpers

    /**
     * Every error response is built here, which makes this the one place worth counting them.
     *
     * <p>Tagged by status only. That is deliberate: {@code MetricsConfig} strips the
     * {@code exception} tag from {@code http.server.requests} precisely because its value set
     * is open-ended, and re-introducing the same unbounded dimension here would undo it. The
     * status code is the bounded, actionable projection — six or so values, and the thing the
     * Phase 5 error-rate monitor actually alerts on.
     */
    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        metrics.counter("jobtracker.api.errors", "status", String.valueOf(status.value()))
                .increment();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://app4jobtrack.me/problems/" + slug(title)));
        return problem;
    }

    private String slug(String title) {
        return title.toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
    }

    /** Jackson's messages can carry a long "at [Source: ...]" tail that helps nobody. */
    private String trim(String message) {
        int marker = message.indexOf(" at [Source");
        String trimmed = marker > 0 ? message.substring(0, marker) : message;
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "…" : trimmed;
    }
}
