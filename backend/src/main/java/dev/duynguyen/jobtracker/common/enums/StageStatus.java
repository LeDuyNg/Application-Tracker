package dev.duynguyen.jobtracker.common.enums;

/** State of a single round (SCHEMA.md §5). */
public enum StageStatus {

    /** Known to be coming, no date yet. */
    EXPECTED,
    /** Has a {@code scheduledAt}. */
    SCHEDULED,
    /** Completed, advanced. */
    PASSED,
    /** Completed, did not advance. */
    FAILED,
    /** Called off, will not happen. */
    CANCELLED,
    /** Moved; a newer stage entry supersedes this one. */
    RESCHEDULED,
    /** Ghosted on this specific round. */
    NO_RESPONSE;

    /**
     * A round the pipeline is still waiting on. The lowest-sequence stage matching this is
     * what {@code currentStageType} points at (SCHEMA.md §1).
     */
    public boolean isPending() {
        return this == EXPECTED || this == SCHEDULED;
    }

    /** {@code completedAt} must be non-null for these (SCHEMA.md §8.1). */
    public boolean isCompleted() {
        return this == PASSED || this == FAILED;
    }
}
