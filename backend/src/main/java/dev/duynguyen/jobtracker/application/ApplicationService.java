package dev.duynguyen.jobtracker.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.duynguyen.jobtracker.application.dto.ApplicationResponse;
import dev.duynguyen.jobtracker.application.dto.CreateApplicationRequest;
import dev.duynguyen.jobtracker.application.dto.StageRequest;
import dev.duynguyen.jobtracker.application.dto.UpdateApplicationRequest;
import dev.duynguyen.jobtracker.common.BadRequestException;
import dev.duynguyen.jobtracker.common.NotFoundException;
import dev.duynguyen.jobtracker.common.Tags;
import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;
import dev.duynguyen.jobtracker.company.Company;
import dev.duynguyen.jobtracker.company.CompanyRepository;

/**
 * Application CRUD and stage management.
 *
 * <p>This class owns the four fields that are not simply what the caller sent — the three
 * denormalized ones plus {@code lastContactAt}. Every mutation funnels through
 * {@link #syncDerivedFields}, so there is exactly one place these rules live (SCHEMA.md §1).
 * Each has a unit test, because each is easy to get subtly wrong in a way no compiler
 * catches and no manual click-through reveals.
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final CompanyRepository companies;
    private final ApplicationMapper mapper;
    private final TimeService time;

    ApplicationService(ApplicationRepository applications, CompanyRepository companies,
                       ApplicationMapper mapper, TimeService time) {
        this.applications = applications;
        this.companies = companies;
        this.mapper = mapper;
        this.time = time;
    }

    // ---------------------------------------------------------------- CRUD

    public ApplicationResponse get(String id) {
        return mapper.toResponse(require(id));
    }

    public ApplicationResponse create(CreateApplicationRequest r) {
        Company company = requireCompany(r.companyId());

        Application a = new Application();
        a.setCompanyId(company.getId());
        a.setCompanyName(company.getName());
        applyOwnFields(a, r.role(), r.appliedDate(), r.source(), r.followUpDate(), r.jobPostingUrl(),
                r.location(), r.workMode(), r.notes(), r.tags());
        a.setCompensation(validateCompensation(mapper.toEntity(r.compensation())));

        // Optional on create, defaulting to ACTIVE (SCHEMA.md §3, §8.1).
        a.setStatus(r.status() != null ? r.status() : ApplicationStatus.ACTIVE);

        a.setStages(r.stages() == null || r.stages().isEmpty()
                ? List.of(seedSubmissionStage(a))
                : buildStages(r.stages()));
        seedLastContact(a);

        syncDerivedFields(a);
        return mapper.toResponse(applications.save(a));
    }

    public ApplicationResponse update(String id, UpdateApplicationRequest r) {
        Application a = require(id);

        if (!a.getCompanyId().equals(r.companyId())) {
            Company company = requireCompany(r.companyId());
            a.setCompanyId(company.getId());
            a.setCompanyName(company.getName());
        }

        applyOwnFields(a, r.role(), r.appliedDate(), r.source(), r.followUpDate(), r.jobPostingUrl(),
                r.location(), r.workMode(), r.notes(), r.tags());
        a.setCompensation(validateCompensation(mapper.toEntity(r.compensation())));

        // An explicit status wins outright — this is how WITHDRAWN and GHOSTED are ever set,
        // since no rule derives them. Setting a terminal value here also freezes future
        // recomputation, via the isTerminal() guard in syncStatus.
        a.setStatus(r.status());

        // NOTE: stages are deliberately untouched here — UpdateApplicationRequest has no
        // stages field. See its javadoc.
        syncDerivedFields(a);
        return mapper.toResponse(applications.save(a));
    }

    public void delete(String id) {
        applications.delete(require(id));
    }

    // -------------------------------------------------------------- Stages

    public ApplicationResponse addStage(String applicationId, StageRequest r) {
        Application a = require(applicationId);
        List<Stage> stages = new ArrayList<>(a.getStages());

        Stage stage = new Stage();
        stage.setStageId(UUID.randomUUID().toString());
        mapper.apply(stage, r);
        validateStage(stage);

        // Omitted sequence means "append", which is the normal case. An explicit sequence
        // inserts at that position and pushes the rest down.
        int position = r.sequence() != null
                ? Math.min(r.sequence() - 1, stages.size())
                : stages.size();
        stages.add(position, stage);
        resequence(stages);

        a.setStages(stages);
        touchContact(a);
        syncDerivedFields(a);
        return mapper.toResponse(applications.save(a));
    }

    public ApplicationResponse updateStage(String applicationId, String stageId, StageRequest r) {
        Application a = require(applicationId);
        Stage stage = a.getStages().stream()
                .filter(s -> s.getStageId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("Stage", stageId));

        StageStatus previousStatus = stage.getStatus();
        var previousScheduled = stage.getScheduledAt();
        var previousCompleted = stage.getCompletedAt();

        mapper.apply(stage, r);
        validateStage(stage);

        if (r.sequence() != null && r.sequence() != stage.getSequence()) {
            List<Stage> stages = new ArrayList<>(a.getStages());
            stages.remove(stage);
            stages.add(Math.min(r.sequence() - 1, stages.size()), stage);
            resequence(stages);
            a.setStages(stages);
        }

        // Only a change to status or the dates counts as contact — editing notes must not
        // reset the "gone quiet" clock (SCHEMA.md §1).
        boolean movedTheProcess = previousStatus != stage.getStatus()
                || !java.util.Objects.equals(previousScheduled, stage.getScheduledAt())
                || !java.util.Objects.equals(previousCompleted, stage.getCompletedAt());
        if (movedTheProcess) {
            touchContact(a);
        }

        syncDerivedFields(a);
        return mapper.toResponse(applications.save(a));
    }

    public ApplicationResponse deleteStage(String applicationId, String stageId) {
        Application a = require(applicationId);
        List<Stage> stages = new ArrayList<>(a.getStages());

        boolean removed = stages.removeIf(s -> s.getStageId().equals(stageId));
        if (!removed) {
            throw NotFoundException.of("Stage", stageId);
        }
        if (stages.isEmpty()) {
            throw new BadRequestException(
                    "An application must keep at least one stage. Delete the application instead.");
        }

        resequence(stages);
        a.setStages(stages);
        syncDerivedFields(a);
        return mapper.toResponse(applications.save(a));
    }

    // ------------------------------------------------- Derived-field rules

    /** Recomputes everything this service owns. Call after any mutation. */
    void syncDerivedFields(Application a) {
        syncCurrentStageType(a);
        syncStatus(a);
    }

    /**
     * <strong>Rule 1.</strong> {@code currentStageType} = the type of the
     * <em>lowest</em>-sequence stage still pending, else the <em>highest</em>-sequence
     * stage that passed.
     *
     * <p>Not "the latest pending stage": if round 4 is SCHEDULED and round 5 is already
     * pencilled in as EXPECTED, the round you are actually in is 4. Taking the latest would
     * report a round you have not reached (SCHEMA.md §1).
     */
    private void syncCurrentStageType(Application a) {
        a.getStages().stream()
                .filter(s -> s.getStatus() != null && s.getStatus().isPending())
                .min(Comparator.comparingInt(Stage::getSequence))
                .map(Stage::getType)
                .or(() -> a.getStages().stream()
                        .filter(s -> s.getStatus() == StageStatus.PASSED)
                        .max(Comparator.comparingInt(Stage::getSequence))
                        .map(Stage::getType))
                .ifPresent(a::setCurrentStageType);
    }

    /**
     * <strong>Rule 2.</strong> Status is derived from the stages — but only while it is
     * still ACTIVE or OFFER.
     *
     * <p>Terminal statuses are sticky. Without this guard, marking an application WITHDRAWN
     * and later fixing a typo on an old stage would silently flip it back to ACTIVE,
     * quietly resurrecting applications you had closed.
     *
     * <p>ACCEPTED, WITHDRAWN and GHOSTED are never derived — each is a human decision that
     * leaves no trace in the stage data.
     */
    private void syncStatus(Application a) {
        if (a.getStatus() != null && a.getStatus().isTerminal()) {
            return;
        }
        boolean failed = a.getStages().stream().anyMatch(s -> s.getStatus() == StageStatus.FAILED);
        if (failed) {
            a.setStatus(ApplicationStatus.REJECTED);
            return;
        }
        boolean offerExtended = a.getStages().stream()
                .anyMatch(s -> s.getType() == StageType.OFFER && s.getStatus() == StageStatus.PASSED);
        a.setStatus(offerExtended ? ApplicationStatus.OFFER : ApplicationStatus.ACTIVE);
    }

    /**
     * <strong>Rule 3.</strong> {@code lastContactAt} moves only when the process actually
     * moved — a stage added, or a stage's status/dates changed. Never on a notes, tag or
     * compensation edit.
     *
     * <p>This is why the field exists at all instead of reusing {@code updatedAt}, which
     * auditing bumps on every write. "Which companies haven't I heard back from in 2+
     * weeks?" is one of the four headline queries (SCHEMA.md §1, §10.2).
     */
    private void touchContact(Application a) {
        a.setLastContactAt(time.now());
    }

    // ------------------------------------------------------------- Helpers

    private void applyOwnFields(Application a, String role, java.time.LocalDate appliedDate,
                                dev.duynguyen.jobtracker.common.enums.ApplicationSource source,
                                java.time.LocalDate followUpDate, String jobPostingUrl, String location,
                                dev.duynguyen.jobtracker.common.enums.WorkMode workMode,
                                String notes, List<String> tags) {
        if (appliedDate != null && appliedDate.isAfter(time.today())) {
            // Re-checked here because @PastOrPresent compares against the JVM's default
            // zone, which is not necessarily app.timezone (SCHEMA.md §7).
            throw new BadRequestException("appliedDate cannot be in the future");
        }
        a.setRole(role == null ? null : role.trim());
        a.setAppliedDate(appliedDate);
        a.setSource(source);
        a.setFollowUpDate(followUpDate);
        a.setJobPostingUrl(jobPostingUrl);
        a.setLocation(location);
        a.setWorkMode(workMode);
        a.setNotes(notes);
        a.setTags(Tags.normalize(tags));
    }

    /**
     * The first stage of every application, created when the caller supplies none.
     *
     * <p>{@code completedAt} is midnight of {@code appliedDate} <em>in the owner's
     * timezone</em>, not UTC — otherwise the submission lands on the wrong calendar day.
     *
     * <p>{@code lastContactAt} is set here too: submitting is the last thing that happened
     * in this process, so an application that never gets a reply correctly ages into the
     * "gone quiet" query rather than being invisible to it.
     */
    private Stage seedSubmissionStage(Application a) {
        Stage stage = new Stage();
        stage.setStageId(UUID.randomUUID().toString());
        stage.setSequence(1);
        stage.setType(StageType.APPLICATION_SUBMITTED);
        stage.setStatus(StageStatus.PASSED);
        stage.setCompletedAt(time.startOfDay(a.getAppliedDate()));
        a.setLastContactAt(stage.getCompletedAt());
        return stage;
    }

    /**
     * Gives every new application a {@code lastContactAt}, including one created with its
     * stages supplied.
     *
     * <p>{@link #seedSubmissionStage} already sets the field, but it only runs when the
     * caller sends <em>no</em> stages. An application created with its rounds attached —
     * which is exactly what the Phase 4 backfill of the in-progress job search does — would
     * otherwise be saved with a null {@code lastContactAt} and never appear in the gone-quiet
     * query, because {@code null} is not {@code $lte} any date. Silently invisible, and only
     * in the historical data, which is the hardest place to notice it.
     *
     * <p>The value is the furthest-forward thing that has happened on any stage. A future
     * {@code scheduledAt} counts and is meant to: a booked interview is not a process that
     * has gone quiet.
     */
    private void seedLastContact(Application a) {
        if (a.getLastContactAt() != null) {
            return;
        }
        a.setLastContactAt(a.getStages().stream()
                .flatMap(s -> java.util.stream.Stream.of(s.getCompletedAt(), s.getScheduledAt()))
                .filter(java.util.Objects::nonNull)
                .max(java.time.Instant::compareTo)
                .orElseGet(() -> time.startOfDay(a.getAppliedDate())));
    }

    private List<Stage> buildStages(List<StageRequest> requests) {
        List<Stage> stages = new ArrayList<>();
        for (StageRequest r : requests) {
            Stage stage = new Stage();
            stage.setStageId(UUID.randomUUID().toString());
            mapper.apply(stage, r);
            validateStage(stage);
            stages.add(stage);
        }
        // Honour explicitly supplied ordering, then renumber contiguously.
        List<Stage> ordered = new ArrayList<>(stages);
        for (int i = 0; i < requests.size(); i++) {
            Integer seq = requests.get(i).sequence();
            if (seq != null) {
                ordered.get(i).setSequence(seq);
            } else {
                ordered.get(i).setSequence(i + 1);
            }
        }
        ordered.sort(Comparator.comparingInt(Stage::getSequence));
        resequence(ordered);
        return ordered;
    }

    /** Keeps sequences 1..n and contiguous, preserving current order. */
    private void resequence(List<Stage> stages) {
        for (int i = 0; i < stages.size(); i++) {
            stages.get(i).setSequence(i + 1);
        }
    }

    /** The conditional date rules Bean Validation cannot express (SCHEMA.md §8.1). */
    private void validateStage(Stage stage) {
        if (stage.getStatus() == StageStatus.SCHEDULED && stage.getScheduledAt() == null) {
            throw new BadRequestException("scheduledAt is required when a stage's status is SCHEDULED");
        }
        if (stage.getStatus() != null && stage.getStatus().isCompleted() && stage.getCompletedAt() == null) {
            throw new BadRequestException(
                    "completedAt is required when a stage's status is " + stage.getStatus());
        }
    }

    private Compensation validateCompensation(Compensation c) {
        if (c != null && c.getMin() != null && c.getMax() != null && c.getMax() < c.getMin()) {
            throw new BadRequestException("compensation.max must be greater than or equal to compensation.min");
        }
        return c;
    }

    private Company requireCompany(String companyId) {
        return companies.findById(companyId)
                .orElseThrow(() -> new BadRequestException("companyId " + companyId + " does not exist"));
    }

    Application require(String id) {
        return applications.findById(id).orElseThrow(() -> NotFoundException.of("Application", id));
    }
}
