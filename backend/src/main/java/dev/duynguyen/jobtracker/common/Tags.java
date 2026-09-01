package dev.duynguyen.jobtracker.common;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Tag normalization, shared by companies and applications.
 *
 * <p>Lives in {@code common/} rather than on one of the feature mappers: both features need
 * it, and having {@code application/} reach into {@code company/} for a helper would couple
 * two packages that are otherwise independent (CLAUDE.md §5, package by feature).
 */
public final class Tags {

    private Tags() {}

    /**
     * Trims, lowercases and de-duplicates, dropping nulls and blanks.
     *
     * <p>Normalizing on save rather than on read means a filter for {@code "referral"} finds
     * everything, regardless of how it was typed. Both collections store tags this way
     * (SCHEMA.md §2, §3).
     */
    public static List<String> normalize(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();
    }
}
