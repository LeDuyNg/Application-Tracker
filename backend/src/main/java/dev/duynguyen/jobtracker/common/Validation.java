package dev.duynguyen.jobtracker.common;

/**
 * Constraint constants shared across request DTOs.
 *
 * <p>These have to be compile-time constants, because that is all a Bean Validation
 * annotation attribute can hold — which is also why they live in a class of their own
 * rather than on one of the DTOs that use them.
 */
public final class Validation {

    /**
     * Any URL the API stores and the SPA later renders into an {@code href}.
     *
     * <p><strong>The scheme allowlist is the point.</strong> Without it {@code
     * javascript:alert(1)} is a perfectly valid 1000-character string, and the SPA's
     * "open ↗" link then executes it in the application's own origin — a stored XSS whose
     * only saving grace is that this app has one writer. {@code @Pattern} is evaluated with
     * {@code Matcher.matches()}, so this must describe the <em>whole</em> value; the outer
     * {@code ?} lets an empty string through, since a blank field is a missing URL rather
     * than a malformed one (the SPA sends {@code null}, but the MCP and {@code .http}
     * clients need not).
     *
     * <p>{@code frontend/src/lib/url.ts} refuses the same schemes at the render site. Two
     * independent checks on purpose: this one cannot protect rows written before it existed.
     */
    public static final String HTTP_URL = "(?i)^(?:https?://\\S+)?$";

    /** Companion message — {@code @Pattern}'s default quotes the regex at the user. */
    public static final String HTTP_URL_MESSAGE = "must be an http:// or https:// URL";

    private Validation() {}
}
