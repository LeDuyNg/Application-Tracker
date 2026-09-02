package dev.duynguyen.jobtracker.config;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.config.MeterFilter;

/**
 * What reaches Datadog, and — more to the point — what does not.
 *
 * <p><strong>Every metric Micrometer pushes is a billable custom metric.</strong> The
 * student Pro plan allots ~100 custom timeseries per host and there is one host
 * (CLAUDE.md §6). Datadog counts unique metric-name + tag-value combinations, not metric
 * names, so the budget is spent by cardinality rather than by how many things you decided
 * to measure.
 *
 * <p>Left alone, Spring Boot's default binders blow through that without anyone choosing to.
 * {@code jvm.memory.used} alone carries {@code area} × {@code id} across every memory pool —
 * around sixteen series — and it has committed and max siblings. Add {@code jvm.gc.*},
 * {@code jvm.threads.states}, {@code tomcat.*}, {@code logback.events} and the pool metrics
 * from the Mongo driver's listeners, and a completely idle application is already over
 * budget. Nothing warns you: Datadog bills the overage, or the plan caps and metrics start
 * silently disappearing.
 *
 * <p>So this is an <strong>allowlist, not a blocklist</strong>. A blocklist means every new
 * dependency that ships a Micrometer binder quietly enlarges the bill; an allowlist means a
 * new metric arrives only when someone adds its name here, which is the decision point you
 * want.
 *
 * <h2>The budget, counted</h2>
 * <table>
 *   <tr><th>Metric</th><th>Cardinality</th><th>Series</th></tr>
 *   <tr><td>{@code jobtracker.applications.created}</td><td>untagged</td><td>1</td></tr>
 *   <tr><td>{@code jobtracker.stages.added}</td><td>× 14 stage types</td><td>≤ 14</td></tr>
 *   <tr><td>{@code jobtracker.api.errors}</td><td>× ~6 status codes</td><td>≤ 6</td></tr>
 *   <tr><td>{@code http.server.requests}</td><td>uri × method × status, expanded by the
 *       registry into count/avg/max</td><td>~40–60</td></tr>
 *   <tr><td>five JVM/system vitals</td><td>{@code jvm.memory.used} keeps area × id</td>
 *       <td>~20</td></tr>
 * </table>
 *
 * <p>That lands near 100 in the worst case and well under it in practice, since a series
 * only exists once that combination has actually occurred — with one user, most of the
 * uri × method × status grid is never hit. Check the real number in Datadog under
 * <em>Plan &amp; Usage → Custom Metrics</em> after a day of traffic rather than trusting
 * this table.
 */
@Configuration
public class MetricsConfig {

    /**
     * Everything kept that is not ours.
     *
     * <p>Deliberately small. {@code jvm.memory.used} earns its ~16 series on a box with a
     * 256 MB heap where the failure mode is the OOM killer. {@code http.server.requests}
     * earns the most of any single entry, because its {@code uri} tag is what answers
     * "which endpoint is slow" — the one question APM would have answered, and the reason
     * dropping APM (§6) costs less than it sounds like.
     */
    private static final Set<String> ALLOWED_THIRD_PARTY = Set.of(
            "http.server.requests",
            "jvm.memory.used",
            "jvm.threads.live",
            "process.uptime",
            "process.cpu.usage",
            "system.cpu.usage");

    /** Our own metrics all share this prefix, and are all deliberately low-cardinality. */
    private static final String OWN_PREFIX = "jobtracker.";

    @Bean
    MeterFilter customMetricBudget() {
        return MeterFilter.denyUnless(id ->
                id.getName().startsWith(OWN_PREFIX) || ALLOWED_THIRD_PARTY.contains(id.getName()));
    }

    /**
     * Strips the two highest-cardinality dimensions from {@code http.server.requests}.
     *
     * <p>{@code outcome} is derivable from {@code status} — {@code SUCCESS} is just
     * {@code 2xx} — so it multiplies the series count while carrying no information the
     * dashboard cannot recompute. {@code exception} is worse: it is the simple class name of
     * whatever was thrown, so its value set is open-ended and grows with the codebase. A tag
     * whose cardinality is bounded only by "how many exception types exist" is exactly the
     * shape that quietly consumes a fixed budget.
     *
     * <p>Error <em>counts</em> are not lost — {@code jobtracker.api.errors} carries them,
     * tagged by status, from {@code GlobalExceptionHandler}, where the mapping to a status
     * code is already decided.
     */
    @Bean
    MeterFilter httpRequestCardinality() {
        return MeterFilter.ignoreTags("outcome", "exception");
    }
}
