package dev.duynguyen.jobtracker.common;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under the {@code app.*} prefix.
 *
 * <p>Only {@code timezone} is used so far; the auth fields arrive in Phase 2.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * The owner's timezone — every "this week" / "due soon" / "in the next N days" boundary
     * is computed here before being converted to UTC for the query (SCHEMA.md §7).
     */
    private ZoneId timezone = ZoneId.of("America/New_York");

    public ZoneId getTimezone() { return timezone; }
    public void setTimezone(ZoneId timezone) { this.timezone = timezone; }
}
