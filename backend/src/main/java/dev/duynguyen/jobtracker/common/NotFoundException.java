package dev.duynguyen.jobtracker.common;

/** Maps to 404 in {@code GlobalExceptionHandler}. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }

    public static NotFoundException of(String what, String id) {
        return new NotFoundException(what + " " + id + " not found");
    }
}
