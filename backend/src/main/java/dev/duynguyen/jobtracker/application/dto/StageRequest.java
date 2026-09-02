package dev.duynguyen.jobtracker.application.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import dev.duynguyen.jobtracker.common.enums.StageFormat;
import dev.duynguyen.jobtracker.common.enums.StageStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;

/**
 * One round, on create of an application or via the stage sub-resource
 * ({@code POST}/{@code PATCH} {@code /api/applications/{id}/stages}). SCHEMA.md §4.3.
 *
 * <p><strong>{@code scheduledAt} is deliberately not {@code @Future}.</strong> Backfilling a
 * job search already in progress means entering interviews that already happened, and a
 * future-only constraint would reject exactly that data (CLAUDE.md §6). The only date rules
 * are conditional and live in the service: {@code scheduledAt} non-null when status is
 * {@code SCHEDULED}, {@code completedAt} non-null when status is {@code PASSED}/{@code FAILED}.
 *
 * <p>{@code sequence} is optional: omit it and the service appends the stage at the end,
 * which is what you want almost always. Supply it only to insert a round out of order.
 *
 * <p>On {@code PATCH}, this is a <em>full replacement</em> of the stage's fields, not a
 * partial merge — a null here clears the value. Records cannot distinguish "absent" from
 * "explicitly null" without wrapper types, and full replacement is the honest, predictable
 * reading of the two.
 */
public record StageRequest(

        @NotNull StageType type,
        @NotNull StageStatus status,
        @Positive Integer sequence,
        Instant scheduledAt,
        Instant completedAt,
        StageFormat format,
        @Size(max = 20) List<@Size(max = 120) String> interviewers,
        @Size(max = 5000) String notes
) {}
