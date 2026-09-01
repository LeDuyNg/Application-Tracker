package dev.duynguyen.jobtracker.application.dto;

import java.time.Instant;
import java.util.List;

import dev.duynguyen.jobtracker.common.enums.StageFormat;
import dev.duynguyen.jobtracker.common.enums.StageStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;

/** One round as returned by the API. {@code stageId} is the handle for PATCH/DELETE. */
public record StageResponse(
        String stageId,
        int sequence,
        StageType type,
        StageStatus status,
        Instant scheduledAt,
        Instant completedAt,
        StageFormat format,
        List<String> interviewers,
        String notes
) {}
