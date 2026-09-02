package dev.duynguyen.jobtracker.company.dto;

import java.time.Instant;
import java.util.List;

/**
 * A company as returned by the API.
 *
 * <p>Entities are never returned directly from controllers (CLAUDE.md §11) — this record is
 * the contract, and {@code frontend/src/api/types.ts} mirrors it field for field.
 */
public record CompanyResponse(
        String id,
        String name,
        String website,
        String industry,
        String location,
        List<ContactResponse> contacts,
        String notes,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {}
