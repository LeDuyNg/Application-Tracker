package dev.duynguyen.jobtracker.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import dev.duynguyen.jobtracker.application.Application;
import dev.duynguyen.jobtracker.application.Stage;
import dev.duynguyen.jobtracker.common.enums.ApplicationSource;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageFormat;
import dev.duynguyen.jobtracker.common.enums.StageStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;

/**
 * Fixture builder for the read-query ITs.
 *
 * <p>These tests are about <em>which documents a query returns</em>, so the fixtures are
 * built and saved directly rather than going through {@code ApplicationService}. That is
 * deliberate: routing them through the write path would mean the service's derived-field
 * rules silently rewrite {@code status}, {@code currentStageType} and {@code lastContactAt},
 * and a test could no longer state plainly what is in the database. Those rules have their
 * own unit tests.
 *
 * <p>The builder sets only what a test names, so each fixture reads as the one property it
 * is there to exercise.
 */
public final class Fixtures {

    private Fixtures() {}

    public static Builder application(String role) {
        return new Builder(role);
    }

    public static final class Builder {

        private final Application a = new Application();
        private final List<Stage> stages = new ArrayList<>();

        private Builder(String role) {
            a.setRole(role);
            a.setCompanyId("company-default");
            a.setCompanyName("Default Co");
            a.setStatus(ApplicationStatus.ACTIVE);
            a.setAppliedDate(LocalDate.of(2026, 8, 1));
            a.setSource(ApplicationSource.COLD_APPLY);
        }

        public Builder company(String id, String name) {
            a.setCompanyId(id);
            a.setCompanyName(name);
            return this;
        }

        public Builder status(ApplicationStatus status) {
            a.setStatus(status);
            return this;
        }

        public Builder applied(String isoDate) {
            a.setAppliedDate(LocalDate.parse(isoDate));
            return this;
        }

        public Builder followUp(String isoDate) {
            a.setFollowUpDate(isoDate == null ? null : LocalDate.parse(isoDate));
            return this;
        }

        public Builder lastContact(String isoInstant) {
            a.setLastContactAt(isoInstant == null ? null : Instant.parse(isoInstant));
            return this;
        }

        public Builder notes(String notes) {
            a.setNotes(notes);
            return this;
        }

        /** A round with no date — enough to make the application well-formed. */
        public Builder stage(StageType type, StageStatus status) {
            return stage(type, status, null, null);
        }

        public Builder stage(StageType type, StageStatus status, String scheduledAt, String completedAt) {
            Stage s = new Stage();
            s.setStageId("stage-" + (stages.size() + 1));
            s.setSequence(stages.size() + 1);
            s.setType(type);
            s.setStatus(status);
            s.setScheduledAt(scheduledAt == null ? null : Instant.parse(scheduledAt));
            s.setCompletedAt(completedAt == null ? null : Instant.parse(completedAt));
            s.setFormat(StageFormat.VIDEO);
            s.setInterviewers(List.of("Priya N. (Staff Eng)"));
            stages.add(s);
            return this;
        }

        public Application build() {
            if (stages.isEmpty()) {
                stage(StageType.APPLICATION_SUBMITTED, StageStatus.PASSED, null, "2026-08-01T04:00:00Z");
            }
            a.setStages(List.copyOf(stages));
            return a;
        }
    }
}
