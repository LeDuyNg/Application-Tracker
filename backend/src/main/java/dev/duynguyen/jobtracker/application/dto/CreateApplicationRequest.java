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
 * Body of {@code POST /api/applications} (SCHEMA.md §8.1).
 *
 * <p><strong>{@code status} and {@code stages} are optional here</strong>, which differs from
 * a literal reading of SCHEMA.md §8.1 — see the note in that section. Requiring
 * {@code status} would make the documented {@code ACTIVE} default unreachable, and requiring
 * a non-empty {@code stages} would make the service's "seed an
 * {@code APPLICATION_SUBMITTED} stage on create" behaviour dead code. The <em>entity</em>
 * still always has at least one stage; the service guarantees that, not the caller.
 *
 * <p>{@code appliedDate} is {@code @PastOrPresent} against the JVM's default zone, which is
 * not necessarily {@code app.timezone}. The service re-checks it against
 * {@code TimeService.today()}; this annotation is a cheap first pass (SCHEMA.md §7).
 */
public record CreateApplicationRequest(

        @NotBlank String companyId,
        @NotBlank @Size(max = 160) String role,

        /** Optional — defaults to {@link ApplicationStatus#ACTIVE}. */
        ApplicationStatus status,

        @NotNull @PastOrPresent LocalDate appliedDate,
        @NotNull ApplicationSource source,

        LocalDate followUpDate,
        @Size(max = 1000) @Pattern(regexp = Validation.HTTP_URL,
                message = Validation.HTTP_URL_MESSAGE) String jobPostingUrl,
        @Size(max = 200) String location,
        WorkMode workMode,
        @Valid CompensationRequest compensation,
        @Size(max = 10000) String notes,
        @Size(max = 20) List<@Size(max = 40) String> tags,

        /** Optional — omit and the service seeds an APPLICATION_SUBMITTED stage. */
        @Size(max = 50) List<@Valid StageRequest> stages
) {}
