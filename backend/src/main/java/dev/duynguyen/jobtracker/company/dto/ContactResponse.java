package dev.duynguyen.jobtracker.company.dto;

/** A contact as returned by the API (SCHEMA.md §4.1). */
public record ContactResponse(
        String name,
        String title,
        String email,
        String phone,
        String notes
) {}
