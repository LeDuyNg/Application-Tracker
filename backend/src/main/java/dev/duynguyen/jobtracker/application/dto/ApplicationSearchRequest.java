package dev.duynguyen.jobtracker.application.dto;

import java.time.LocalDate;

import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;

/**
 * The filter bundle for {@code GET /api/applications} (SCHEMA.md §10.3). Every field is
 * optional; supplying none lists everything.
 *
 * <p>A record rather than five loose method parameters: they are always passed together,
 * and a five-{@code String} call site is exactly the shape that lets two arguments get
 * silently swapped.
 *
 * <p>Paging and sorting are <em>not</em> here — they arrive as a {@code Pageable}, which
 * Spring resolves from {@code page}/{@code size}/{@code sort} on its own.
 *
 * @param q         free text, matched case-insensitively against company name, role and
 *                  notes. Escaped before it reaches Mongo — see the search method.
 * @param status    exact application status
 * @param companyId all applications at one company
 * @param from      inclusive lower bound on {@code appliedDate}
 * @param to        inclusive upper bound on {@code appliedDate}
 */
public record ApplicationSearchRequest(
        String q,
        ApplicationStatus status,
        String companyId,
        LocalDate from,
        LocalDate to
) {}
