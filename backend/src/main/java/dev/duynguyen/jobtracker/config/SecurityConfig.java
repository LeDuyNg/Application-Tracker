package dev.duynguyen.jobtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import dev.duynguyen.jobtracker.auth.AllowlistOidcUserService;
import dev.duynguyen.jobtracker.auth.BearerTokenFilter;
import dev.duynguyen.jobtracker.auth.ProblemDetailAuthHandler;
import dev.duynguyen.jobtracker.common.AppProperties;

/**
 * Two filter chains, split by credential type (CLAUDE.md §6).
 *
 * <p>The app has two kinds of caller with almost nothing in common: a browser holding a
 * session cookie, and the MCP server holding a static bearer token. A single chain would
 * have to serve both, which means CSRF and session management configured for the browser
 * while the token path needs neither, and the token's read-only rule expressed as
 * {@code @PreAuthorize} scattered across every GET-backed service method. Splitting on
 * {@code securityMatcher} makes that rule <em>one line</em> — {@code GET} requires
 * {@code ROLE_MCP}, everything else is denied — and removes the filter-ordering hazard
 * entirely.
 *
 * <p>Order matters only in that the bearer chain must be consulted first; its matcher is
 * narrow (an {@code Authorization: Bearer} header), so everything else falls through to the
 * browser chain.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** springdoc's paths. Permitted on the `local` profile only — see {@link #browserChain}. */
    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"
    };

    /**
     * <strong>Chain 1 — the MCP server.</strong> Stateless, no CSRF, read-only.
     *
     * <p>CSRF is off because there is no cookie and therefore no cross-site request forgery
     * to prevent: the attack requires a credential the browser attaches automatically, and a
     * bearer token is not one. Sessions are off for the same reason — a token authenticates
     * each request on its own, and creating a session per MCP call would leak memory for no
     * benefit.
     *
     * <p>{@code denyAll()} on everything but {@code GET} is the read-only guarantee in full.
     * A valid token attempting a write is authenticated but not authorized, so it is a 403,
     * not a 401 — the difference matters when debugging the MCP server, because it tells you
     * the token was accepted and the operation was refused.
     */
    @Bean
    @Order(1)
    SecurityFilterChain bearerChain(HttpSecurity http, BearerTokenFilter bearerTokenFilter,
                                    ProblemDetailAuthHandler authHandler) throws Exception {
        return http
                .securityMatcher(request -> BearerTokenFilter.presentedToken(request) != null)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/**").hasRole("MCP")
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authHandler)
                        .accessDeniedHandler(authHandler))
                .build();
    }

    /**
     * <strong>Chain 2 — the browser.</strong> Google login, session cookie, CSRF on.
     *
     * <p>Swagger is permitted <em>only</em> when the {@code local} profile is active. It is
     * also disabled outright in prod by {@code springdoc.api-docs.enabled=false} (Phase 1),
     * so this is the second of two independent reasons it cannot be reached on the public
     * domain. Belt and braces on purpose: the property is one line away from being flipped
     * back by someone debugging production, and publishing the whole API surface is a large
     * consequence for a small mistake.
     */
    @Bean
    @Order(2)
    SecurityFilterChain browserChain(HttpSecurity http, AllowlistOidcUserService userService,
                                     ProblemDetailAuthHandler authHandler,
                                     AppProperties appProperties,
                                     Environment environment) throws Exception {

        // Since Spring Security 6 the CSRF token is generated lazily, so the XSRF-TOKEN
        // cookie is not written until something actually reads the token. An SPA that loads
        // and immediately POSTs would then get a 403 that looks like a bug in its own code.
        // Setting the attribute name to null opts out of that deferred loading, so every
        // response carries the cookie. This one line is worth an afternoon (PLAN.md Phase 2).
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        boolean local = environment.matchesProfiles("local");

        // Where to send the browser after a successful Google login. It MUST be an absolute
        // URL to the SPA's origin, not a root-relative "/". In local dev the browser sits on
        // the Vite dev server (:5173) and only reaches Spring (:8080) through Vite's proxy;
        // a relative "/" in the redirect resolves against :8080, landing the user on the
        // bare API where "/" matches nothing and denyAll() returns a naked 403. `app.base-url`
        // is http://localhost:5173 locally and the real origin in prod, so this is correct
        // in both. `alwaysUse` because an SPA has no meaningful "the page you were on".
        SimpleUrlAuthenticationSuccessHandler loginSuccessHandler =
                new SimpleUrlAuthenticationSuccessHandler(
                        appProperties.getBaseUrl().replaceAll("/+$", "") + "/");
        loginSuccessHandler.setAlwaysUseDefaultTargetUrl(true);

        return http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/login/**", "/oauth2/**", "/actuator/health").permitAll();
                    if (local) {
                        auth.requestMatchers(SWAGGER_PATHS).permitAll();
                    }
                    auth.requestMatchers("/api/**").authenticated();
                    auth.anyRequest().denyAll();
                })
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(userService))
                        // Land back on the SPA's origin, not on a "/" that resolves to the
                        // bare API behind the dev proxy — see loginSuccessHandler above.
                        .successHandler(loginSuccessHandler)
                        // The default on failure is a redirect to /login?error, which on an
                        // API means a rejected account sees a 302 to a page that does not
                        // exist. Throwing OAuth2AuthenticationException alone does not change
                        // that — the handler does.
                        .failureHandler((request, response, exception) ->
                                authHandler.handle(request, response,
                                        new org.springframework.security.access.AccessDeniedException(
                                                exception.getMessage(), exception))))
                // POST /logout, protected by CSRF like any other state change — the SPA
                // sends the X-XSRF-TOKEN header. Returns 204 rather than the default 302 to
                // /login?logout (a page that does not exist on an API); the SPA navigates to
                // the landing page itself once the session is gone.
                .logout(logout -> logout
                        // Under /api so it rides the SPA's existing dev proxy and api client
                        // (base path /api) with no extra wiring. The LogoutFilter runs ahead
                        // of authorization, so this needs no permitAll rule.
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID"))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                // authenticationEntryPoint: no session -> 401 problem+json (not a 302 to
                // Google). accessDeniedHandler: an authenticated request that still fails
                // authorization -> 403 problem+json, not Tomcat's naked 403 page. The bearer
                // chain wires both; this one omitted the second by oversight.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authHandler)
                        .accessDeniedHandler(authHandler))
                .build();
    }
}
