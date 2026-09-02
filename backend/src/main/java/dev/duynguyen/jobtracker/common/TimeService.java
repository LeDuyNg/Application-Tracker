package dev.duynguyen.jobtracker.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The single place relative-date math happens (SCHEMA.md §7).
 *
 * <p>Two reasons this exists rather than calling {@code Instant.now()} at each site:
 *
 * <ol>
 *   <li><strong>Correctness.</strong> "This week", "due soon" and "in the next N days" are
 *       questions about the <em>owner's</em> calendar, not UTC's. Computing a boundary in
 *       UTC and comparing it to a {@code LocalDate} stored at local midnight produces
 *       off-by-one errors that only show up near midnight — the worst kind to debug.
 *   <li><strong>Testability.</strong> Everything goes through an injected {@link Clock}, so
 *       a test can fix "now" with {@code Clock.fixed(...)} and assert boundary behaviour
 *       exactly, instead of computing expected values from the wall clock and hoping.
 * </ol>
 */
@Service
public class TimeService {

    private final Clock clock;
    private final ZoneId zone;

    /**
     * {@code @Autowired} is required here purely because this class has two constructors —
     * with more than one and none annotated, Spring falls back to looking for a no-arg
     * constructor and fails. This marks which one it should use.
     */
    @Autowired
    public TimeService(AppProperties properties) {
        this(Clock.systemUTC(), properties.getTimezone());
    }

    /** For tests: {@code new TimeService(Clock.fixed(...), ZoneId.of("America/New_York"))}. */
    public TimeService(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public ZoneId zone() { return zone; }

    /** Now, as a true point in time. */
    public Instant now() { return clock.instant(); }

    /** Today in the owner's timezone — not UTC's today, which can be a day ahead. */
    public LocalDate today() { return LocalDate.ofInstant(clock.instant(), zone); }

    public LocalDate todayPlusDays(long days) { return today().plusDays(days); }

    public Instant nowPlusDays(long days) { return now().plus(java.time.Duration.ofDays(days)); }

    public Instant nowMinusDays(long days) { return now().minus(java.time.Duration.ofDays(days)); }

    /**
     * Midnight of the given date <em>in the owner's timezone</em>, as an Instant.
     *
     * <p>Used when a date-only field has to become a timestamp — e.g. the seeded
     * {@code APPLICATION_SUBMITTED} stage's {@code completedAt} derived from
     * {@code appliedDate}. Using UTC midnight instead would place the event on the wrong
     * calendar day for anyone west of Greenwich.
     */
    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }
}
