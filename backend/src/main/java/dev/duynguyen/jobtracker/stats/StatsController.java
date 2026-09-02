package dev.duynguyen.jobtracker.stats;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.duynguyen.jobtracker.stats.dto.StatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The funnel and the derived rates.
 *
 * <p><strong>Two ways to express a window, and they are mutually exclusive.</strong>
 * {@code days} is a rolling look-back; {@code from}/{@code to} is a calendar range. Both are
 * needed because "how many applications did I send this month?" means a <em>calendar</em>
 * month, which {@code days=30} cannot express — and answering a calendar question with a
 * rolling window while calling it "this month" is a wrong answer stated confidently
 * (SCHEMA.md §10.1). Sending both is a 400, raised by the service.
 *
 * <p>The response echoes the window it actually used, including a human-readable
 * {@code description}, so the MCP tool can quote it rather than assert one.
 */
@RestController
@RequestMapping("/api/stats")
@Tag(name = "Stats", description = "Funnel, response/ghost/offer rates, active pipeline")
public class StatsController {

    private final StatsService stats;

    StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping
    @Operation(summary = "Application funnel and rates",
            description = "Pass `days` for a rolling window, or `from`/`to` for a calendar "
                    + "range, or neither for all time. Passing both is a 400.")
    public StatsResponse stats(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.compute(days, from, to);
    }
}
