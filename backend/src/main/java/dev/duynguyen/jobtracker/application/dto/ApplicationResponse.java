package dev.duynguyen.jobtracker.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.duynguyen.jobtracker.common.enums.ApplicationSource;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;
import dev.duynguyen.jobtracker.common.enums.WorkMode;

/**
 * The full application, including its stages — what {@code GET /api/applications/{id}} and
 * the detail page use. One round trip renders the whole pipeline, which is the point of
 * embedding stages rather than referencing them (SCHEMA.md §1).
 */
public record ApplicationResponse(
        String id,
        String companyId,
        String companyName,
        String role,
        ApplicationStatus status,
        LocalDate appliedDate,
        ApplicationSource source,
        StageType currentStageType,
        LocalDate followUpDate,
        String jobPostingUrl,
        String location,
        WorkMode workMode,
        CompensationResponse compensation,
        String notes,
        List<String> tags,
        List<StageResponse> stages,
        Instant lastContactAt,
        Instant createdAt,
        Instant updatedAt
) {}
