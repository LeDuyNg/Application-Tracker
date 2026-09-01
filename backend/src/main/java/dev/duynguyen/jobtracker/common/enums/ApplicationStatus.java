package dev.duynguyen.jobtracker.common.enums;

/**
 * Overall state of an application (SCHEMA.md §5).
 *
 * <p>This value is <em>denormalized</em>: it is derived from what has happened in
 * {@code stages[]}, but it can also be set explicitly by the user. The rule that keeps the
 * two from fighting each other is {@link #isTerminal()} — see the class comment on
 * {@code ApplicationService}.
 */
public enum ApplicationStatus {

    /** In progress. */
    ACTIVE,
    /** Offer extended, not yet decided. Still non-terminal — it can still become anything. */
    OFFER,
    /** You accepted an offer. */
    ACCEPTED,
    /** The company rejected you. */
    REJECTED,
    /** You pulled out. */
    WITHDRAWN,
    /** No response for a long time and you have given up. Set manually, never derived. */
    GHOSTED;

    /**
     * Terminal statuses are "sticky": once set, the service must not recompute the status
     * from {@code stages[]} again. Without this, marking an application {@code WITHDRAWN}
     * and later fixing a typo in an old stage's notes would silently flip it back to
     * {@code ACTIVE} (SCHEMA.md §1).
     *
     * <p>Also used by the follow-ups query, which excludes terminal applications
     * (SCHEMA.md §10.2).
     */
    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == WITHDRAWN || this == GHOSTED;
    }
}
