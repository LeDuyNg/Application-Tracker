package dev.duynguyen.jobtracker.application.dto;

import java.util.List;

/**
 * What needs chasing, behind {@code GET /api/applications/followups} and the
 * {@code list_pending_followups} MCP tool (SCHEMA.md §10.2).
 *
 * <p><strong>Two groups, deliberately kept apart rather than merged into one list.</strong>
 * They answer different questions and a reader should not have to guess which is which:
 *
 * <ul>
 *   <li>{@code due} — you set a {@code followUpDate} and it has arrived. An explicit
 *       reminder you left yourself.
 *   <li>{@code goneQuiet} — nobody has moved this process in weeks. Nothing was scheduled;
 *       the silence itself is the signal. This is what answers "which companies haven't I
 *       heard back from in 2+ weeks?", one of the four headline queries.
 * </ul>
 *
 * <p>An application can appear in <em>both</em> lists — a follow-up you scheduled for a
 * pipeline that has also gone silent is, if anything, the most urgent case there is. The
 * lists are not deduplicated against each other on purpose.
 *
 * @param due           applications whose {@code followUpDate} has arrived, soonest first
 * @param goneQuiet     active applications with no contact in a while, quietest first
 * @param dueWithinDays the look-ahead used for {@code due}, echoed so a caller can say it
 * @param quietAfterDays the silence threshold used for {@code goneQuiet}, likewise
 */
public record FollowupResponse(
        List<Due> due,
        List<GoneQuiet> goneQuiet,
        int dueWithinDays,
        int quietAfterDays
) {

    /**
     * @param application the application to chase; its {@code followUpDate} is the due date
     * @param daysOverdue days past {@code followUpDate}. <strong>Negative means not yet
     *                    due</strong> — the list looks ahead a week, so upcoming reminders
     *                    are included rather than hidden until the morning they land.
     */
    public record Due(ApplicationSummaryResponse application, long daysOverdue) {}

    /**
     * @param application      the silent application; its {@code lastContactAt} is when it
     *                         last moved
     * @param daysSinceContact whole 24-hour days since then — the same unit the query's
     *                         threshold uses, so the number and the filter always agree
     */
    public record GoneQuiet(ApplicationSummaryResponse application, long daysSinceContact) {}
}
