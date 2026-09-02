package dev.duynguyen.jobtracker.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Who is logged in.
 *
 * <p>The SPA calls this on load for two reasons: to show the signed-in address, and to find
 * out whether the session is still valid. A 401 here is the SPA's cue to send the user to
 * {@code /oauth2/authorization/google} — which is why the API returns 401 rather than
 * redirecting on its own (see {@link ProblemDetailAuthHandler}).
 */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Auth", description = "The signed-in user")
public class MeController {

    /**
     * @param principal the OIDC user, injected by Spring Security from the session. Null
     *                  when the caller is the MCP server, which is authenticated by token
     *                  and is not a person.
     */
    @GetMapping
    @Operation(summary = "The signed-in user's profile",
            description = "401 when there is no session. The MCP token authenticates a "
                    + "service, not a person, so it gets an empty profile rather than one "
                    + "invented for it.")
    public MeResponse me(@AuthenticationPrincipal OidcUser principal) {
        if (principal == null) {
            return new MeResponse(null, null, null, false);
        }
        return new MeResponse(
                principal.getEmail(),
                principal.getFullName(),
                principal.getPicture(),
                true);
    }

    /**
     * @param person true for a human session, false for the MCP token. Lets the SPA tell
     *               "logged in as nobody" apart from "logged in as someone" without having
     *               to infer it from null fields.
     */
    public record MeResponse(String email, String name, String picture, boolean person) {}
}
