package dev.duynguyen.jobtracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import dev.duynguyen.jobtracker.common.AppProperties;
import dev.duynguyen.jobtracker.support.AbstractMongoIT;

/**
 * The security boundary itself: who gets in, and what they may do once in.
 *
 * <p>Everything here is a <em>negative</em> assertion or a status-code distinction, because
 * that is what security tests are for. The read-only guarantee for the MCP token in
 * particular is one line of configuration — {@code anyRequest().denyAll()} — and a single
 * careless edit turns it into full write access with no other symptom.
 */
@AutoConfigureMockMvc
class SecurityIT extends AbstractMongoIT {

    @Autowired private MockMvc mvc;
    @Autowired private AppProperties properties;

    private String bearer() {
        return "Bearer " + properties.getMcpToken();
    }

    // ------------------------------------------------------- no credential

    @Nested
    @DisplayName("without any credential")
    class Unauthenticated {

        @Test
        @DisplayName("/api returns 401 JSON — never a redirect to Google")
        void apiReturns401NotRedirect() throws Exception {
            MvcResult result = mvc.perform(get("/api/applications"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andReturn();

            // A 302 here would be the framework default and would break the SPA: fetch()
            // follows it, fails CORS against accounts.google.com, and surfaces as an
            // unreadable network error instead of "you are logged out".
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        }

        // The XSRF-TOKEN cookie assertion lives in CsrfCookieIT — it needs a context no
        // .with(csrf()) test has touched. See that class for why.

        @Test
        @DisplayName("health is open on loopback only; Google's login entry point is reachable")
        void publicEndpoints() throws Exception {
            // MockMvc's default remote address is 127.0.0.1, i.e. the deploy smoke check.
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());

            // The same URL from anywhere else is not public. Guards the case where
            // management.endpoint.health.show-details gets flipped to `always` while
            // debugging — the body would then carry the Mongo wire version, the JAR's
            // path on disk and the volume's free space.
            mvc.perform(get("/actuator/health").with(request -> {
                        request.setRemoteAddr("203.0.113.9");
                        return request;
                    }))
                    .andExpect(status().isUnauthorized());

            mvc.perform(get("/oauth2/authorization/google"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("an unmapped path is denied, not merely 404'd")
        void unmappedPathsDenied() throws Exception {
            mvc.perform(get("/internal/whatever"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------- MCP bearer

    @Nested
    @DisplayName("with the MCP bearer token")
    class Bearer {

        @Test
        @DisplayName("GET is allowed")
        void getIsAllowed() throws Exception {
            mvc.perform(get("/api/applications").header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/stats").header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/applications/followups").header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk());
        }

        /**
         * The read-only guarantee, method by method. This is the test that matters most in
         * the file: CLAUDE.md §14 lists "writes from the MCP server" as an explicit non-goal,
         * and the whole enforcement is one {@code denyAll()}.
         *
         * <p>403 rather than 401 throughout — the token <em>is</em> valid, the operation is
         * not permitted. That distinction is what tells you, when debugging the MCP server,
         * that your credential was accepted.
         */
        @Test
        @DisplayName("every write verb is refused with 403, including the stage sub-resources")
        void writesAreRefused() throws Exception {
            String auth = bearer();
            String body = """
                    { "name": "Should Never Be Created" }""";

            mvc.perform(post("/api/companies").header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/companies/{id}", "abc").header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/companies/{id}", "abc").header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isForbidden());

            mvc.perform(post("/api/applications").header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/applications/{id}", "abc").header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/applications/{id}", "abc").header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isForbidden());

            mvc.perform(post("/api/applications/{id}/stages", "abc").header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/applications/{id}/stages/{s}", "abc", "def")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/applications/{id}/stages/{s}", "abc", "def")
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a CSRF token does not buy the bearer chain a write")
        void csrfDoesNotUnlockWrites() throws Exception {
            // The bearer chain has CSRF disabled, so a caller might reasonably guess that
            // supplying one changes something. It must not: authorization is what refuses
            // this, not CSRF.
            mvc.perform(post("/api/companies").with(csrf())
                            .header(HttpHeaders.AUTHORIZATION, bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "name": "Still Not Created" }"""))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a wrong token is 401, and a right one of the wrong length is too")
        void wrongTokenIsUnauthorized() throws Exception {
            mvc.perform(get("/api/applications").header(HttpHeaders.AUTHORIZATION, "Bearer nonsense"))
                    .andExpect(status().isUnauthorized());

            // A prefix of the real token must not pass. If the comparison ever became a
            // startsWith, or the digest step were dropped, this is what would catch it.
            String truncated = properties.getMcpToken().substring(0, properties.getMcpToken().length() - 1);
            mvc.perform(get("/api/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + truncated))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("an empty bearer token never authenticates")
        void emptyTokenRejected() throws Exception {
            // Guards the isBlank() check: a deployment that forgot APP_MCP_TOKEN would
            // otherwise match "" against "" and hand the whole API to anyone who guessed.
            mvc.perform(get("/api/applications").header(HttpHeaders.AUTHORIZATION, "Bearer "))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("the bearer chain is stateless — no session cookie is issued")
        void noSessionForTokenCallers() throws Exception {
            MvcResult result = mvc.perform(get("/api/applications")
                            .header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getRequest().getSession(false))
                    .as("a session per MCP call would leak memory for no benefit")
                    .isNull();
        }
    }
}
