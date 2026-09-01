package dev.duynguyen.jobtracker.common;

/**
 * Maps to 400. For rules Bean Validation cannot express on a single field — cross-field
 * checks, conditional requirements, and referential checks (SCHEMA.md §8.1).
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
