package dev.duynguyen.jobtracker.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.duynguyen.jobtracker.common.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates the MCP server's static bearer token.
 *
 * <p>The MCP server runs headless as a subprocess of Claude Desktop, so it cannot complete
 * an interactive Google redirect. It gets a separate, non-interactive credential instead —
 * and a strictly read-only one, enforced by a single request-matcher line in the bearer
 * filter chain rather than by authorization annotations scattered over the services
 * (CLAUDE.md §6).
 *
 * <p><strong>The comparison is constant-time, and on digests rather than the raw strings.</strong>
 * {@code String.equals} returns as soon as two bytes differ, so the time it takes to fail
 * leaks how much of the token was guessed correctly — enough, over many requests, to
 * reconstruct it a character at a time. {@link MessageDigest#isEqual} does not short-circuit
 * on content, but it does compare lengths first, which still leaks the token's length. Hashing
 * both sides to a fixed 32 bytes removes that too, and costs nothing at this request volume.
 *
 * <p>A missing or wrong token is left <em>unauthenticated</em> rather than rejected here.
 * The filter's job is to establish identity; deciding what happens without one belongs to
 * the chain's entry point, which returns a 401 in the same shape as every other error.
 */
@Component
public class BearerTokenFilter extends OncePerRequestFilter {

    public static final String PREFIX = "Bearer ";
    public static final String ROLE = "ROLE_MCP";

    private final AppProperties properties;

    BearerTokenFilter(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String presented = presentedToken(request);
        String expected = properties.getMcpToken();

        // An unset token must never authenticate anyone. Without this guard a deployment
        // that forgot APP_MCP_TOKEN would accept an empty bearer header as valid.
        if (presented != null && !expected.isBlank() && matches(presented, expected)) {
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    "mcp-server", null, List.of(new SimpleGrantedAuthority(ROLE)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    /**
     * @return the token, or null when the header is absent or not a bearer header
     *
     * <p>Public because {@code SecurityConfig} uses it as the bearer chain's
     * {@code securityMatcher}: the same predicate decides which chain handles a request and
     * which token that chain then checks, so the two cannot drift apart.
     */
    public static String presentedToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith(PREFIX) ? header.substring(PREFIX.length()) : null;
    }

    private boolean matches(String presented, String expected) {
        return MessageDigest.isEqual(sha256(presented), sha256(expected));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM implementation; if it is missing the platform
            // is broken in ways this filter cannot paper over.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
