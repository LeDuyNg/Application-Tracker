package dev.duynguyen.jobtracker.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import dev.duynguyen.jobtracker.common.enums.ApplicationSource;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;
import dev.duynguyen.jobtracker.common.enums.WorkMode;

/**
 * One role applied to at one company (SCHEMA.md §3). The collection the dashboard and all
 * four MCP tools operate on.
 *
 * <p><strong>Three fields here are denormalized</strong> and are the service layer's job to
 * keep honest — never a controller's, and never the MCP server's (which is read-only):
 * {@link #companyName}, {@link #status}, and {@link #currentStageType}. Plus
 * {@link #lastContactAt}, which is not a copy of anything but is likewise service-maintained.
 * The rules are in SCHEMA.md §1; each has a unit test.
 */
@Document("applications")
public class Application {

    @Id
    private String id;

    /** References {@code companies._id}. Existence is validated by the service, not by Mongo. */
    private String companyId;

    /**
     * Denormalized copy of {@code companies.name}, so list views and free-text search need
     * no lookup. Re-written across all of a company's applications when it is renamed.
     */
    private String companyName;

    private String role;

    /** Derived from {@code stages[]} — but terminal values are sticky. See {@code ApplicationStatus.isTerminal()}. */
    private ApplicationStatus status;

    /** Date-only: avoids off-by-one bugs in "this month" math (SCHEMA.md §7). */
    private LocalDate appliedDate;

    private ApplicationSource source;

    /**
     * Denormalized: the round you are in or waiting on. Recomputed on every stage change as
     * the lowest-{@code sequence} pending stage, else the highest-{@code sequence} passed one.
     */
    private StageType currentStageType;

    /** When to chase. Powers {@code list_pending_followups}. */
    private LocalDate followUpDate;

    private String jobPostingUrl;
    private String location;
    private WorkMode workMode;
    private Compensation compensation;
    private String notes;

    /** Lowercased on save by the service. */
    private List<String> tags = new ArrayList<>();

    /** Ordered rounds. Always at least one — create seeds an APPLICATION_SUBMITTED stage. */
    private List<Stage> stages = new ArrayList<>();

    /**
     * Last time the <em>process</em> moved: a stage added, or a stage's status/dates changed.
     *
     * <p>Deliberately <strong>not</strong> {@link #updatedAt}. Auditing bumps {@code updatedAt}
     * on any write, so fixing a typo in {@code notes} would reset it — and "which companies
     * haven't I heard back from in 2+ weeks?" is one of the four headline queries this
     * project exists to answer (SCHEMA.md §1, §10.2).
     */
    private Instant lastContactAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public LocalDate getAppliedDate() { return appliedDate; }
    public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }

    public ApplicationSource getSource() { return source; }
    public void setSource(ApplicationSource source) { this.source = source; }

    public StageType getCurrentStageType() { return currentStageType; }
    public void setCurrentStageType(StageType currentStageType) { this.currentStageType = currentStageType; }

    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate followUpDate) { this.followUpDate = followUpDate; }

    public String getJobPostingUrl() { return jobPostingUrl; }
    public void setJobPostingUrl(String jobPostingUrl) { this.jobPostingUrl = jobPostingUrl; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public WorkMode getWorkMode() { return workMode; }
    public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }

    public Compensation getCompensation() { return compensation; }
    public void setCompensation(Compensation compensation) { this.compensation = compensation; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public List<Stage> getStages() { return stages; }
    public void setStages(List<Stage> stages) {
        this.stages = stages == null ? new ArrayList<>() : stages;
    }

    public Instant getLastContactAt() { return lastContactAt; }
    public void setLastContactAt(Instant lastContactAt) { this.lastContactAt = lastContactAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
