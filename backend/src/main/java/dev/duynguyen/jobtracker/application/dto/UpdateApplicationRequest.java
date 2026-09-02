package dev.duynguyen.jobtracker.application.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import dev.duynguyen.jobtracker.common.Validation;
import dev.duynguyen.jobtracker.common.enums.ApplicationSource;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.WorkMode;

/**
 * Body of {@code PUT /api/applications/{id}} — a full replacement of the application's own
 * fields.
 *
 * <p><strong>{@code stages} is absent on purpose.</strong> Rounds are managed through the
 * sub-resource ({@code POST}/{@code PATCH}/{@code DELETE}
 * {@code /api/applications/{id}/stages[/{stageId}]}) so there is exactly one code path that
 * mutates them — the same path that maintains {@code currentStageType},
 * {@code lastContactAt} and the derived {@code status}. Letting a PUT replace the array
 * wholesale would be a second path that has to repeat all three rules, and would silently
 * discard {@code stageId}s the frontend and MCP server hold references to.
 *
 * <p>{@code status} <em>is</em> settable here: that is how a user marks something
 * {@code WITHDRAWN} or {@code GHOSTED}, neither of which any rule derives. Setting a
 * terminal status also freezes future recomputation (SCHEMA.md §1).
 */
public record UpdateApplicationRequest(

        @NotBlank String companyId,
        @NotBlank @Size(max = 160) String role,
        @NotNull ApplicationStatus status,
        @NotNull @PastOrPresent LocalDate appliedDate,
        @NotNull ApplicationSource source,

        LocalDate followUpDate,
        @Size(max = 1000) @Pattern(regexp = Validation.HTTP_URL,
                message = Validation.HTTP_URL_MESSAGE) String jobPostingUrl,
        @Size(max = 200) String location,
        WorkMode workMode,
        @Valid CompensationRequest compensation,
        @Size(max = 10000) String notes,
        @Size(max = 20) List<@Size(max = 40) String> tags
) {}
