package dev.duynguyen.jobtracker.auth;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Makes Spring Security's rejections look like every other error the API returns.
 *
 * <p><strong>401, never a redirect to Google.</strong> Security's default entry point for an
 * {@code oauth2Login} chain sends a 302 to the provider, which is right for a browser typing
 * a URL and wrong for everything else: the SPA's {@code fetch} would follow it, fail CORS
 * against accounts.google.com, and surface as an unreadable network error rather than
 * "you are logged out". The SPA decides when to start a login; the API's job is to say
 * plainly that there is no session (PLAN.md Phase 2).
 *
 * <p>Doubles as the {@link AccessDeniedHandler} so an authenticated-but-forbidden request —
 * the MCP token attempting a write — comes back as a 403 in the same shape.
 */
@Component
public class ProblemDetailAuthHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;

    ProblemDetailAuthHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Authentication is required. Sign in at /oauth2/authorization/google.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Forbidden",
                "This credential is not permitted to perform that operation.");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://app4jobtrack.me/problems/"
                + title.toLowerCase(java.util.Locale.ROOT)));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), problem);
    }
}
