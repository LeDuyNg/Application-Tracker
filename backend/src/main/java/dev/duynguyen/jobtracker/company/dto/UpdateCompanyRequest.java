package dev.duynguyen.jobtracker.company.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/companies/{id}} — a full replacement, so every field is sent and
 * omitting one clears it. Deliberately the same shape as {@link CreateCompanyRequest}
 * rather than a shared type: they are free to diverge (a create might later take a
 * "seed from URL" field that an update must not), and one is not a subtype of the other.
 *
 * <p>Renaming a company here re-writes the denormalized {@code companyName} on every one of
 * its applications — see {@code CompanyService} (SCHEMA.md §1).
 */
public record UpdateCompanyRequest(

        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String website,
        @Size(max = 120) String industry,
        @Size(max = 200) String location,
        @Valid List<ContactRequest> contacts,
        @Size(max = 5000) String notes,
        List<@Size(max = 40) String> tags
) {}
