package dev.duynguyen.jobtracker.common;

/** Maps to 409. Used for duplicate company names and for blocked deletes. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
