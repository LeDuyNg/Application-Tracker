package dev.duynguyen.jobtracker.company.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import dev.duynguyen.jobtracker.common.Validation;

/**
 * Body of {@code POST /api/companies} (SCHEMA.md §8.1).
 *
 * <p>{@code @Valid} on the <em>type argument</em> is what makes validation recurse into the
 * list — without it the constraints on {@link ContactRequest} are never evaluated. Easy to
 * miss, and it fails open. Note the position: {@code @Valid List<ContactRequest>} also
 * cascades today, but Hibernate Validator deprecated it (HV000271) and warns on every
 * startup, so the annotation is on the element type where it will keep working.
 */
public record CreateCompanyRequest(

        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) @Pattern(regexp = Validation.HTTP_URL,
                message = Validation.HTTP_URL_MESSAGE) String website,
        @Size(max = 120) String industry,
        @Size(max = 200) String location,
        @Size(max = 20) List<@Valid ContactRequest> contacts,
        @Size(max = 5000) String notes,
        @Size(max = 20) List<@Size(max = 40) String> tags
) {}
