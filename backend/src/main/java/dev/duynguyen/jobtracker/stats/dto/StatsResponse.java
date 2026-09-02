package dev.duynguyen.jobtracker.stats.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;

/**
 * The funnel and derived rates (SCHEMA.md §9), behind {@code GET /api/stats} and the
 * {@code get_application_stats} MCP tool.
 *
 * @param window            what was actually measured — see {@link Window}
 * @param totalApplications applications in the window
 * @param byStatus          count per status; statuses with no applications are omitted
 * @param funnel            how far applications got, in {@code StageType} declaration order
 * @param activePipeline    applications still in play
 * @param responseRatePct   share that got past the initial submission, 0–100
 * @param ghostRatePct      share old enough to be considered ghosted that never got a reply
 * @param offerRatePct      share that reached OFFER or ACCEPTED
 * @param avgDaysToFirstResponse mean days from applying to first activity; null if nothing has responded
 */
public record StatsResponse(
        Window window,
        long totalApplications,
        Map<ApplicationStatus, Long> byStatus,
        List<FunnelEntry> funnel,
        long activePipeline,
        double responseRatePct,
        double ghostRatePct,
        double offerRatePct,
        Double avgDaysToFirstResponse
) {

    /**
     * The window the numbers describe, echoed back deliberately.
     *
     * <p>The MCP tool must be able to say <em>which</em> window it used: "how many
     * applications this month?" means a calendar month, and answering with a rolling 30 days
     * while calling it "this month" is a wrong answer stated confidently (SCHEMA.md §10.1).
     * {@code description} is human-readable text the tool can quote directly.
     */
    public record Window(LocalDate from, LocalDate to, String description) {}

    /** One bar of the funnel: how many applications got no further than this stage type. */
    public record FunnelEntry(StageType stageType, long count) {}
}
