package dev.duynguyen.jobtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * Web-layer limits that are not expressible on a DTO.
 *
 * <p>Spring Data's pageable resolver defaults {@code max-page-size} to <strong>2000</strong>,
 * and {@code @PageableDefault(size = 20)} only sets the default — it does not cap what a
 * caller may ask for. {@code GET /api/applications?size=2000} therefore materialises two
 * thousand documents plus their embedded {@code stages[]} into a list of DTOs in one
 * request. That is harmless on a laptop and is not on a 1 GB Oracle micro running with
 * {@code -Xmx256m} (CLAUDE.md §6), where it is a one-line way to OOM the JVM.
 *
 * <p>100 is well above anything the SPA's pagination asks for (20) and still bounded. The
 * resolver clamps rather than rejects, so an over-large request quietly returns 100 instead
 * of failing — which is the right behaviour for a limit that exists to protect the server,
 * not to police the caller.
 */
@Configuration
public class WebConfig {

    /** Above the SPA's page size by 5x, below anything that threatens a 256 MB heap. */
    private static final int MAX_PAGE_SIZE = 100;

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> resolver.setMaxPageSize(MAX_PAGE_SIZE);
    }
}
