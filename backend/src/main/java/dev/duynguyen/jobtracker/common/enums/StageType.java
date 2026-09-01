package dev.duynguyen.jobtracker.common.enums;

/**
 * One round in an application's pipeline (SCHEMA.md §5).
 *
 * <p><strong>Declaration order is canonical and load-bearing.</strong> It is the pipeline's
 * natural progression, and the stats funnel renders in it — {@link #ordinal()} is how the
 * funnel sorts "how far did this application get". Adding a constant in the wrong place
 * silently reorders the funnel, so insert new rounds at the position they actually occur.
 * {@code CLAUDE.md §7} and {@code SCHEMA.md §5} repeat this list; keep all three in sync.
 */
public enum StageType {

    /** The initial submission. Normally {@code stages[0]}, seeded on create. */
    APPLICATION_SUBMITTED,
    /** Non-technical intro call. */
    RECRUITER_SCREEN,
    /** Automated coding/aptitude test. */
    ONLINE_ASSESSMENT,
    /** Take-home assignment. */
    TAKE_HOME,
    /** Technical phone screen. */
    PHONE_SCREEN,
    /** Live coding / technical round. */
    TECHNICAL_INTERVIEW,
    /** Design round. */
    SYSTEM_DESIGN,
    /** Behavioral / values round. */
    BEHAVIORAL,
    /** Hiring-manager conversation. */
    HIRING_MANAGER,
    /** Onsite loop / final panel — one stage by default, several if you want per-interview detail. */
    SUPERDAY,
    /** Post-loop team matching. */
    TEAM_MATCH,
    REFERENCE_CHECK,
    /** Offer stage: paperwork, negotiation. */
    OFFER,
    OTHER
}
