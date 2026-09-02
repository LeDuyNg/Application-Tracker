package dev.duynguyen.jobtracker.application.dto;

import java.time.Instant;
import java.time.LocalDate;

import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;

/**
 * Compact shape for list and search results (SCHEMA.md §10.3).
 *
 * <p>Deliberately omits {@code stages}, {@code notes} and {@code compensation}. A search can
 * return dozens of rows and the MCP server shapes its answer from this — sending every
 * stage of every match would bloat both the JSON and, for the MCP path, the context window
 * Claude has to read.
 */
public record ApplicationSummaryResponse(
        String id,
        String companyId,
        String companyName,
        String role,
        ApplicationStatus status,
        StageType currentStageType,
        LocalDate appliedDate,
        LocalDate followUpDate,
        Instant lastContactAt
) {}
