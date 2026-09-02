package dev.duynguyen.jobtracker.application;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.duynguyen.jobtracker.application.dto.ApplicationResponse;
import dev.duynguyen.jobtracker.application.dto.ApplicationSearchRequest;
import dev.duynguyen.jobtracker.application.dto.ApplicationSummaryResponse;
import dev.duynguyen.jobtracker.application.dto.CreateApplicationRequest;
import dev.duynguyen.jobtracker.application.dto.FollowupResponse;
import dev.duynguyen.jobtracker.application.dto.StageRequest;
import dev.duynguyen.jobtracker.application.dto.UpcomingInterviewResponse;
import dev.duynguyen.jobtracker.application.dto.UpdateApplicationRequest;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Applications and their stages — the collection nearly everything operates on.
 *
 * <p>Reads split across two services: {@link ApplicationService} for anything that writes,
 * {@link ApplicationQueryService} for the three read features (CLAUDE.md §6). The controller
 * is the only place that knows both.
 *
 * <p><strong>Route ordering is not load-bearing here, but it looks like it should be.</strong>
 * {@code /followups} and {@code /interviews} are literal paths that sit where
 * {@code /{id}} could also match. Spring's {@code PathPatternParser} scores a literal
 * segment above a variable one regardless of declaration order, so these resolve correctly
 * wherever they appear in the file. Worth knowing, because the older {@code AntPathMatcher}
 * did not, and every "put the specific route first" answer online is about that.
 */
@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications", description = "Roles applied to, and the interview rounds each went through")
public class ApplicationController {

    /** A week — the window the dashboard and the MCP tool both default to. */
    private static final int DEFAULT_INTERVIEW_DAYS = 7;

    private final ApplicationService applications;
    private final ApplicationQueryService queries;

    ApplicationController(ApplicationService applications, ApplicationQueryService queries) {
        this.applications = applications;
        this.queries = queries;
    }

    // ----------------------------------------------------- the read features

    /**
     * Filtered, paged list. Every parameter is optional; with none it lists everything,
     * newest first.
     *
     * <p>Returns {@link PagedModel} rather than {@code Page}. Serializing a {@code PageImpl}
     * directly emits Spring Data's internal structure, which it explicitly warns is not a
     * stable contract; {@code PagedModel} is the supported wrapper and gives a
     * {@code {content, page:{size, number, totalElements, totalPages}}} shape the SPA and
     * MCP client can rely on across upgrades.
     */
    @GetMapping
    @Operation(summary = "Search and filter applications",
            description = "`q` matches company name, role and notes case-insensitively, "
                    + "including partial words. Default sort is appliedDate descending.")
    public PagedModel<ApplicationSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {

        // No sort is passed through deliberately: the service supplies appliedDate desc when
        // the caller sends none, so the default lives next to the query it applies to.
        return new PagedModel<>(
                queries.search(new ApplicationSearchRequest(q, status, companyId, from, to), pageable));
    }

    @GetMapping("/followups")
    @Operation(summary = "What needs chasing",
            description = "Two groups: reminders that have come due, and active pipelines "
                    + "that have gone quiet. An application can appear in both.")
    public FollowupResponse followups() {
        return queries.followups();
    }

    @GetMapping("/interviews")
    @Operation(summary = "Scheduled rounds in the next N days, soonest first")
    public List<UpcomingInterviewResponse> upcomingInterviews(
            @RequestParam(defaultValue = "" + DEFAULT_INTERVIEW_DAYS) int days) {
        return queries.upcomingInterviews(days);
    }

    // ------------------------------------------------------------------ CRUD

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one application, including every stage")
    public ApplicationResponse get(@PathVariable String id) {
        return applications.get(id);
    }

    @PostMapping
    @Operation(summary = "Create an application",
            description = "`status` defaults to ACTIVE and `stages` may be omitted — the "
                    + "service seeds an APPLICATION_SUBMITTED stage when none is supplied.")
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse created = applications.create(request);
        return ResponseEntity.created(URI.create("/api/applications/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an application's own fields",
            description = "Stages are not part of this body — they are managed through the "
                    + "stages sub-resource, so exactly one code path maintains the derived fields.")
    public ApplicationResponse update(@PathVariable String id,
                                      @Valid @RequestBody UpdateApplicationRequest request) {
        return applications.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an application and its stages")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        applications.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------- stages sub-resource

    /**
     * All three stage operations return the <em>whole</em> updated application, not the
     * stage. A stage mutation also moves {@code currentStageType}, possibly {@code status},
     * and usually {@code lastContactAt} — returning just the stage would leave every caller
     * holding a stale parent and needing a second GET to find out what else changed.
     */
    @PostMapping("/{id}/stages")
    @Operation(summary = "Add a round. Omit `sequence` to append, which is the normal case.")
    public ResponseEntity<ApplicationResponse> addStage(@PathVariable String id,
                                                        @Valid @RequestBody StageRequest request) {
        ApplicationResponse updated = applications.addStage(id, request);
        return ResponseEntity.created(URI.create("/api/applications/" + id)).body(updated);
    }

    @PatchMapping("/{id}/stages/{stageId}")
    @Operation(summary = "Replace a round's fields",
            description = "A full replacement, not a merge: a null clears the value.")
    public ApplicationResponse updateStage(@PathVariable String id,
                                           @PathVariable String stageId,
                                           @Valid @RequestBody StageRequest request) {
        return applications.updateStage(id, stageId, request);
    }

    @DeleteMapping("/{id}/stages/{stageId}")
    @Operation(summary = "Remove a round. 400 if it is the last one.")
    public ApplicationResponse deleteStage(@PathVariable String id, @PathVariable String stageId) {
        return applications.deleteStage(id, stageId);
    }
}
