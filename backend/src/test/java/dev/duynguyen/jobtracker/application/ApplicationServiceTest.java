package dev.duynguyen.jobtracker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.duynguyen.jobtracker.application.dto.ApplicationResponse;
import dev.duynguyen.jobtracker.application.dto.CreateApplicationRequest;
import dev.duynguyen.jobtracker.application.dto.StageRequest;
import dev.duynguyen.jobtracker.application.dto.UpdateApplicationRequest;
import dev.duynguyen.jobtracker.common.BadRequestException;
import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.common.enums.ApplicationSource;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;
import dev.duynguyen.jobtracker.company.Company;
import dev.duynguyen.jobtracker.company.CompanyRepository;

/**
 * Unit tests for the rules {@link ApplicationService} owns.
 *
 * <p>Mocked repositories and a <strong>fixed clock</strong> — the point is to assert the
 * derived-field logic exactly, including boundaries, without a database or a moving "now".
 * The Testcontainers integration tests cover persistence separately.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final Instant NOW = Instant.parse("2026-09-01T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final String COMPANY_ID = "company-1";

    private SimpleMeterRegistry metrics;

    @Mock private ApplicationRepository applications;
    @Mock private CompanyRepository companies;

    private ApplicationService service;

    @BeforeEach
    void setUp() {
        TimeService time = new TimeService(Clock.fixed(NOW, ZoneId.of("UTC")), ZONE);
        // A real SimpleMeterRegistry rather than a mock: it is in-memory, has no
        // dependencies, and lets the counter assertions below read actual values instead
        // of verifying that increment() was called on a stub.
        metrics = new SimpleMeterRegistry();
        service = new ApplicationService(applications, companies, new ApplicationMapper(), time, metrics);

        // Saving echoes the entity back, so assertions read the object the service built.
        // lenient() because Mockito's strict stubs flag this as unused in the tests that
        // exercise pure logic or expect a validation failure before any save happens.
        lenient().when(applications.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------- Rule 1

    @Nested
    @DisplayName("currentStageType")
    class CurrentStageType {

        @Test
        @DisplayName("is the LOWEST-sequence pending stage, not the latest")
        void picksLowestPendingNotLatest() {
            // Round 4 is scheduled; round 5 is already pencilled in as expected. The round
            // you are actually in is 4 — taking the latest would report SYSTEM_DESIGN, a
            // round that has not happened.
            Application a = applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.RECRUITER_SCREEN, StageStatus.PASSED),
                    stage(3, StageType.TECHNICAL_INTERVIEW, StageStatus.SCHEDULED),
                    stage(4, StageType.SYSTEM_DESIGN, StageStatus.EXPECTED));

            service.syncDerivedFields(a);

            assertThat(a.getCurrentStageType()).isEqualTo(StageType.TECHNICAL_INTERVIEW);
        }

        @Test
        @DisplayName("falls back to the highest-sequence PASSED stage when nothing is pending")
        void fallsBackToHighestPassed() {
            Application a = applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.ONLINE_ASSESSMENT, StageStatus.PASSED));

            service.syncDerivedFields(a);

            assertThat(a.getCurrentStageType()).isEqualTo(StageType.ONLINE_ASSESSMENT);
        }
    }

    // ------------------------------------------------------------- Rule 2

    @Nested
    @DisplayName("status derivation")
    class StatusDerivation {

        @Test
        @DisplayName("a FAILED stage rejects the application")
        void failedStageRejects() {
            Application a = applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.ONLINE_ASSESSMENT, StageStatus.FAILED));
            a.setStatus(ApplicationStatus.ACTIVE);

            service.syncDerivedFields(a);

            assertThat(a.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        }

        @Test
        @DisplayName("a passed OFFER stage moves the application to OFFER")
        void passedOfferStageSetsOffer() {
            Application a = applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.OFFER, StageStatus.PASSED));
            a.setStatus(ApplicationStatus.ACTIVE);

            service.syncDerivedFields(a);

            assertThat(a.getStatus()).isEqualTo(ApplicationStatus.OFFER);
        }

        @Test
        @DisplayName("a terminal status is STICKY — an unrelated stage edit must not revive it")
        void terminalStatusIsSticky() {
            // The regression this guards: mark an application WITHDRAWN, later fix a typo on
            // an old stage, and a naive recompute quietly flips it back to ACTIVE —
            // resurrecting applications you had closed, with no error anywhere.
            Application a = applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.RECRUITER_SCREEN, StageStatus.PASSED));
            a.setStatus(ApplicationStatus.WITHDRAWN);

            service.syncDerivedFields(a);

            assertThat(a.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        }

        @Test
        @DisplayName("GHOSTED is never derived — nothing in the stages implies it")
        void ghostedIsNeverDerived() {
            Application a = applicationWith(stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED));
            a.setStatus(ApplicationStatus.GHOSTED);

            service.syncDerivedFields(a);

            assertThat(a.getStatus()).isEqualTo(ApplicationStatus.GHOSTED);
        }
    }

    // ------------------------------------------------------------- Rule 3

    @Nested
    @DisplayName("lastContactAt")
    class LastContactAt {

        @Test
        @DisplayName("moves when a stage is added")
        void movesWhenStageAdded() {
            Application a = persisted(applicationWith(stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED)));
            a.setLastContactAt(Instant.parse("2026-08-01T00:00:00Z"));

            service.addStage("app-1", new StageRequest(
                    StageType.RECRUITER_SCREEN, StageStatus.EXPECTED, null, null, null, null, null, null));

            assertThat(a.getLastContactAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("moves when a stage's status changes")
        void movesWhenStageStatusChanges() {
            Application a = persisted(applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.RECRUITER_SCREEN, StageStatus.EXPECTED)));
            a.setLastContactAt(Instant.parse("2026-08-01T00:00:00Z"));
            String stageId = a.getStages().get(1).getStageId();

            service.updateStage("app-1", stageId, new StageRequest(
                    StageType.RECRUITER_SCREEN, StageStatus.PASSED, null, null,
                    Instant.parse("2026-08-30T12:00:00Z"), null, null, null));

            assertThat(a.getLastContactAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("does NOT move when only a stage's notes change")
        void doesNotMoveOnNotesOnlyEdit() {
            // The whole reason this field exists instead of reusing updatedAt: editing notes
            // must not reset the "haven't heard back in 2+ weeks" clock (SCHEMA.md §1).
            Instant untouched = Instant.parse("2026-08-01T00:00:00Z");
            Application a = persisted(applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.RECRUITER_SCREEN, StageStatus.EXPECTED)));
            a.setLastContactAt(untouched);
            String stageId = a.getStages().get(1).getStageId();

            service.updateStage("app-1", stageId, new StageRequest(
                    StageType.RECRUITER_SCREEN, StageStatus.EXPECTED, null, null, null, null, null,
                    "prep: read up on their payments stack"));

            assertThat(a.getLastContactAt()).isEqualTo(untouched);
        }
    }

    // ------------------------------------------------------- create/validation

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("seeds an APPLICATION_SUBMITTED stage when the caller supplies none")
        void seedsSubmissionStage() {
            stubCompany();

            ApplicationResponse response = service.create(createRequest(null));

            assertThat(response.stages()).hasSize(1);
            assertThat(response.stages().getFirst().type()).isEqualTo(StageType.APPLICATION_SUBMITTED);
            assertThat(response.stages().getFirst().status()).isEqualTo(StageStatus.PASSED);
            assertThat(response.stages().getFirst().sequence()).isEqualTo(1);
            assertThat(response.status()).isEqualTo(ApplicationStatus.ACTIVE);
        }

        @Test
        @DisplayName("seeded completedAt is local midnight, not UTC midnight")
        void seededStageUsesOwnerTimezone() {
            // 2026-09-01 in America/New_York is 04:00Z, not 00:00Z. Using UTC midnight would
            // place the submission on the previous local day.
            stubCompany();

            ApplicationResponse response = service.create(createRequest(null));

            assertThat(response.stages().getFirst().completedAt())
                    .isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay(ZONE).toInstant());
        }

        @Test
        @DisplayName("sets lastContactAt so a never-answered application ages into 'gone quiet'")
        void seedsLastContactAt() {
            stubCompany();

            ApplicationResponse response = service.create(createRequest(null));

            assertThat(response.lastContactAt()).isNotNull();
        }

        @Test
        @DisplayName("rejects an unknown companyId with 400, not 404")
        void rejectsUnknownCompany() {
            when(companies.findById(COMPANY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(createRequest(null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("does not exist");
        }

        @Test
        @DisplayName("rejects a SCHEDULED stage with no scheduledAt")
        void rejectsScheduledWithoutDate() {
            stubCompany();
            StageRequest bad = new StageRequest(
                    StageType.TECHNICAL_INTERVIEW, StageStatus.SCHEDULED, null, null, null, null, null, null);

            assertThatThrownBy(() -> service.create(createRequest(List.of(bad))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("scheduledAt is required");
        }

        @Test
        @DisplayName("rejects a future appliedDate using the owner's timezone")
        void rejectsFutureAppliedDate() {
            stubCompany();
            CreateApplicationRequest r = new CreateApplicationRequest(
                    COMPANY_ID, "Backend Engineer", null, TODAY.plusDays(1), ApplicationSource.REFERRAL,
                    null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> service.create(r))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("appliedDate");
        }
    }

    @Nested
    @DisplayName("stage sequencing")
    class Sequencing {

        @Test
        @DisplayName("an added stage without a sequence is appended and numbering stays contiguous")
        void appendsAndRenumbers() {
            Application a = persisted(applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.RECRUITER_SCREEN, StageStatus.PASSED)));

            service.addStage("app-1", new StageRequest(
                    StageType.TECHNICAL_INTERVIEW, StageStatus.EXPECTED, null, null, null, null, null, null));

            assertThat(a.getStages()).extracting(Stage::getSequence).containsExactly(1, 2, 3);
            assertThat(a.getStages().get(2).getType()).isEqualTo(StageType.TECHNICAL_INTERVIEW);
        }

        @Test
        @DisplayName("an explicit sequence inserts and pushes later stages down")
        void insertsAtExplicitSequence() {
            Application a = persisted(applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.TECHNICAL_INTERVIEW, StageStatus.EXPECTED)));

            service.addStage("app-1", new StageRequest(
                    StageType.ONLINE_ASSESSMENT, StageStatus.PASSED, 2, null,
                    Instant.parse("2026-08-15T12:00:00Z"), null, null, null));

            assertThat(a.getStages()).extracting(Stage::getType).containsExactly(
                    StageType.APPLICATION_SUBMITTED, StageType.ONLINE_ASSESSMENT, StageType.TECHNICAL_INTERVIEW);
            assertThat(a.getStages()).extracting(Stage::getSequence).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("deleting the last remaining stage is refused")
        void refusesToDeleteFinalStage() {
            Application a = persisted(applicationWith(stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED)));
            String stageId = a.getStages().getFirst().getStageId();

            assertThatThrownBy(() -> service.deleteStage("app-1", stageId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("at least one stage");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("an explicit terminal status is honoured and then sticks")
        void explicitTerminalStatusWins() {
            Application a = persisted(applicationWith(
                    stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED),
                    stage(2, StageType.RECRUITER_SCREEN, StageStatus.PASSED)));

            UpdateApplicationRequest r = new UpdateApplicationRequest(
                    COMPANY_ID, "Backend Engineer", ApplicationStatus.WITHDRAWN, TODAY,
                    ApplicationSource.REFERRAL, null, null, null, null, null, null, null);

            ApplicationResponse response = service.update("app-1", r);

            assertThat(response.status()).isEqualTo(ApplicationStatus.WITHDRAWN);
        }
    }

    // ------------------------------------------------------------- helpers

    private void stubCompany() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Stripe");
        when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company));
    }

    private CreateApplicationRequest createRequest(List<StageRequest> stages) {
        return new CreateApplicationRequest(
                COMPANY_ID, "Backend Engineer", null, TODAY, ApplicationSource.REFERRAL,
                null, null, null, null, null, null, null, stages);
    }

    private Application applicationWith(Stage... stages) {
        Application a = new Application();
        a.setId("app-1");
        a.setCompanyId(COMPANY_ID);
        a.setCompanyName("Stripe");
        a.setRole("Backend Engineer");
        a.setAppliedDate(TODAY);
        a.setSource(ApplicationSource.REFERRAL);
        a.setStatus(ApplicationStatus.ACTIVE);
        a.setStages(new java.util.ArrayList<>(List.of(stages)));
        return a;
    }

    /** Registers the application so {@code findById} returns it, and hands it back for assertions. */
    private Application persisted(Application a) {
        when(applications.findById("app-1")).thenReturn(Optional.of(a));
        return a;
    }

    private Stage stage(int sequence, StageType type, StageStatus status) {
        Stage s = new Stage();
        s.setStageId("stage-" + sequence);
        s.setSequence(sequence);
        s.setType(type);
        s.setStatus(status);
        if (status.isCompleted()) {
            s.setCompletedAt(Instant.parse("2026-08-0%dT12:00:00Z".formatted(Math.min(sequence, 9))));
        }
        if (status == StageStatus.SCHEDULED) {
            s.setScheduledAt(Instant.parse("2026-09-1%dT12:00:00Z".formatted(Math.min(sequence, 9))));
        }
        return s;
    }
}
