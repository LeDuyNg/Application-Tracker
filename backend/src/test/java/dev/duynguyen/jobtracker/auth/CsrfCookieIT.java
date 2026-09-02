package dev.duynguyen.jobtracker.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import dev.duynguyen.jobtracker.support.AbstractMongoIT;

/**
 * One assertion, in a class of its own, with a context nobody else has touched.
 *
 * <p><strong>Why it cannot live in {@code SecurityIT}.</strong> It did, and it failed on the
 * first CI run after passing locally for three phases. Four IT classes share
 * {@code @AutoConfigureMockMvc} on top of {@link AbstractMongoIT}, so Spring caches and
 * reuses <em>one</em> application context across all of them. And
 * {@code SecurityMockMvcRequestPostProcessors.csrf()} does more than decorate a request: it
 * replaces the {@code CsrfTokenRepository} on the shared {@code CsrfFilter} bean with a test
 * double that keeps the token in a request attribute instead of a cookie. That swap outlives
 * the request, the test and the class — it lasts for the rest of the JVM run.
 *
 * <p>So this assertion only held while it ran before <em>every</em> {@code .with(csrf())}
 * test in any of those four classes. Which of them runs first is decided by Maven's
 * {@code runOrder}, whose default is {@code filesystem} — and a filesystem enumerates
 * differently on macOS and on a Linux runner. Hence green on a laptop, red in CI, with no
 * code difference between them. ({@code runOrder} is now pinned to {@code alphabetical} in
 * the POM as well, so the two agree; that alone would have hidden this rather than fixed it.)
 *
 * <p>{@code @DirtiesContext(BEFORE_CLASS)} is the actual fix: a context built fresh for this
 * class, with a {@code CsrfFilter} still holding the real {@link
 * org.springframework.security.web.csrf.CookieCsrfTokenRepository}. It costs one extra
 * context per run and makes the result independent of what else exists.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieIT extends AbstractMongoIT {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("the XSRF-TOKEN cookie is set even on a rejected request")
    void csrfCookieIsNotDeferred() throws Exception {
        // Guards the setCsrfRequestAttributeName(null) line in SecurityConfig. Since Spring
        // Security 6 the token is generated lazily, so without that opt-out this cookie is
        // absent until something reads the token — and an SPA that loads and immediately
        // POSTs gets a 403 that looks like a bug in its own code.
        mvc.perform(get("/api/applications"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }
}
