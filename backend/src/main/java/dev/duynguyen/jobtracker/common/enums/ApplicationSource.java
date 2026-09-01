package dev.duynguyen.jobtracker.common.enums;

/** How an application originated (SCHEMA.md §5). Feeds the "which channels actually work" stat. */
public enum ApplicationSource {
    REFERRAL,
    COLD_APPLY,
    RECRUITER_OUTREACH,
    CAREER_FAIR,
    NETWORKING_EVENT,
    JOB_BOARD,
    COMPANY_WEBSITE,
    OTHER
}
