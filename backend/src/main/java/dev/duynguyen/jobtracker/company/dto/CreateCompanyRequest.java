package dev.duynguyen.jobtracker.company.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/companies} (SCHEMA.md §8.1).
 *
 * <p>{@code @Valid} on {@code contacts} is what makes validation recurse into the list —
 * without it the constraints on {@link ContactRequest} are never evaluated. Easy to miss,
 * and it fails open.
 */
public record CreateCompanyRequest(

        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String website,
        @Size(max = 120) String industry,
        @Size(max = 200) String location,
        @Valid List<ContactRequest> contacts,
        @Size(max = 5000) String notes,
        List<@Size(max = 40) String> tags
) {}
