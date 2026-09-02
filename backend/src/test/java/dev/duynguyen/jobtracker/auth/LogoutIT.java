package dev.duynguyen.jobtracker.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import dev.duynguyen.jobtracker.support.AbstractMongoIT;

/**
 * {@code POST /api/logout} on the browser chain.
 *
 * <p>Its own {@code *IT} rather than a nested class in {@link SecurityIT}: the CSRF request
 * post-processor plus a real logout, added as extra methods there, perturbed the ordering
 * that {@code SecurityIT.csrfCookieIsNotDeferred} quietly depends on ("the first request in
 * the run emits a fresh XSRF-TOKEN cookie"). Kept apart, both are stable.
 */
@AutoConfigureMockMvc
class LogoutIT extends AbstractMongoIT {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("with a CSRF token: 204, no redirect, and JSESSIONID is cleared")
    void logoutReturns204() throws Exception {
        // 204, not the framework default 302 to /login?logout — that page does not exist on
        // an API; the SPA drives the navigation to the landing page itself.
        mvc.perform(post("/api/logout").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(cookie().maxAge("JSESSIONID", 0));
    }

    @Test
    @DisplayName("without a CSRF token: refused")
    void logoutNeedsCsrf() throws Exception {
        // Logout is a state change, so it is CSRF-protected like any write. The SPA's api
        // client attaches X-XSRF-TOKEN on mutating verbs.
        mvc.perform(post("/api/logout"))
                .andExpect(status().isForbidden());
    }
}
