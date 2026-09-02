package dev.duynguyen.jobtracker.common;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under the {@code app.*} prefix.
 *
 * <p>Note that {@code @ConfigurationProperties} ignores unknown keys by default, so a
 * property set in YAML but missing here is not an error — it is simply never read. That is
 * a quiet failure mode: {@code app.mcp-token} sat in {@code application-local.yml} through
 * all of Phase 1 doing nothing, because this class had no field for it.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * The owner's timezone — every "this week" / "due soon" / "in the next N days" boundary
     * is computed here before being converted to UTC for the query (SCHEMA.md §7).
     */
    private ZoneId timezone = ZoneId.of("America/New_York");

    /**
     * The Google accounts permitted to log in. Single-user by design (CLAUDE.md §14), but a
     * list because the property is comma-separated and one day it might not be.
     *
     * <p>Empty means <strong>nobody can log in</strong>, which is the correct default: an
     * allowlist that fails open is not an allowlist. A misconfigured deploy locks the owner
     * out, which is recoverable; the alternative admits the internet.
     */
    private List<String> allowedEmails = List.of();

    /**
     * The static bearer token the MCP server presents. Read-only access, enforced by the
     * bearer filter chain rather than by annotations (CLAUDE.md §6).
     */
    private String mcpToken = "";

    /** Public origin, used to build the OAuth redirect URI in prod. */
    private String baseUrl = "http://localhost:5173";

    public ZoneId getTimezone() { return timezone; }
    public void setTimezone(ZoneId timezone) { this.timezone = timezone; }

    public List<String> getAllowedEmails() { return allowedEmails; }

    /** Normalized on the way in so the comparison at login time is a plain set lookup. */
    public void setAllowedEmails(List<String> allowedEmails) {
        this.allowedEmails = allowedEmails == null ? List.of() : allowedEmails.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    public String getMcpToken() { return mcpToken; }
    public void setMcpToken(String mcpToken) { this.mcpToken = mcpToken == null ? "" : mcpToken.trim(); }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
