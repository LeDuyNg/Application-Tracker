package dev.duynguyen.jobtracker.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A recruiter/referrer on a company create or update (SCHEMA.md §4.1). */
public record ContactRequest(

        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String title,
        @Email @Size(max = 254) String email,
        @Size(max = 40) String phone,
        @Size(max = 2000) String notes
) {}
