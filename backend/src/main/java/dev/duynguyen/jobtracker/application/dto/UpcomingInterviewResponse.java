package dev.duynguyen.jobtracker.application.dto;

import java.time.Instant;
import java.util.List;

import dev.duynguyen.jobtracker.common.enums.StageFormat;
import dev.duynguyen.jobtracker.common.enums.StageType;

/**
 * One scheduled round, behind {@code GET /api/applications/interviews} and the
 * {@code get_upcoming_interviews} MCP tool (SCHEMA.md §10.4).
 *
 * <p>This is a <em>stage</em>-shaped row, not an application-shaped one: the question is
 * "what is on my calendar", so an application with two rounds booked this week appears
 * twice, and the whole result sorts by time across every application. Returning applications
 * with nested stages would make the caller flatten and re-sort to get that.
 *
 * <p>Carries {@code applicationId} and {@code stageId} so the SPA can link straight to the
 * round rather than searching for it.
 */
public record UpcomingInterviewResponse(
        String applicationId,
        String stageId,
        String companyId,
        String companyName,
        String role,
        StageType stageType,
        Instant scheduledAt,
        StageFormat format,
        List<String> interviewers
) {}
