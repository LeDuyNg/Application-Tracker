package dev.duynguyen.jobtracker.application.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Compensation on create/update (SCHEMA.md §4.2). Optional as a whole.
 *
 * <p>{@code max >= min} is <em>not</em> expressible with field annotations, so the service
 * checks it and returns 400. Cross-field rules live there by convention (SCHEMA.md §8.1).
 */
public record CompensationRequest(
        @PositiveOrZero Integer min,
        @PositiveOrZero Integer max,
        @Size(min = 3, max = 3) String currency
) {}
