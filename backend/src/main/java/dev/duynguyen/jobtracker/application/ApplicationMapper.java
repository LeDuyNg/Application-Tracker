package dev.duynguyen.jobtracker.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.duynguyen.jobtracker.application.dto.ApplicationResponse;
import dev.duynguyen.jobtracker.application.dto.ApplicationSummaryResponse;
import dev.duynguyen.jobtracker.application.dto.CompensationRequest;
import dev.duynguyen.jobtracker.application.dto.CompensationResponse;
import dev.duynguyen.jobtracker.application.dto.StageRequest;
import dev.duynguyen.jobtracker.application.dto.StageResponse;
import dev.duynguyen.jobtracker.common.Tags;

/** Hand-written entity ↔ DTO mapping (CLAUDE.md §11). */
@Component
public class ApplicationMapper {

    public ApplicationResponse toResponse(Application a) {
        return new ApplicationResponse(
                a.getId(),
                a.getCompanyId(),
                a.getCompanyName(),
                a.getRole(),
                a.getStatus(),
                a.getAppliedDate(),
                a.getSource(),
                a.getCurrentStageType(),
                a.getFollowUpDate(),
                a.getJobPostingUrl(),
                a.getLocation(),
                a.getWorkMode(),
                toResponse(a.getCompensation()),
                a.getNotes(),
                List.copyOf(a.getTags()),
                a.getStages().stream()
                        // Stored order should already be by sequence, but sorting here means a
                        // response is never wrong just because a write path appended sloppily.
                        .sorted(java.util.Comparator.comparingInt(Stage::getSequence))
                        .map(this::toResponse)
                        .toList(),
                a.getLastContactAt(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }

    public ApplicationSummaryResponse toSummary(Application a) {
        return new ApplicationSummaryResponse(
                a.getId(),
                a.getCompanyId(),
                a.getCompanyName(),
                a.getRole(),
                a.getStatus(),
                a.getCurrentStageType(),
                a.getAppliedDate(),
                a.getFollowUpDate(),
                a.getLastContactAt());
    }

    public StageResponse toResponse(Stage s) {
        return new StageResponse(
                s.getStageId(),
                s.getSequence(),
                s.getType(),
                s.getStatus(),
                s.getScheduledAt(),
                s.getCompletedAt(),
                s.getFormat(),
                List.copyOf(s.getInterviewers()),
                s.getNotes());
    }

    private CompensationResponse toResponse(Compensation c) {
        return c == null ? null : new CompensationResponse(c.getMin(), c.getMax(), c.getCurrency());
    }

    /**
     * Maps a stage request onto a stage entity. Does <em>not</em> set {@code stageId} or
     * {@code sequence} — those are assigned by {@code ApplicationService}, which owns
     * ordering and identity for the array.
     */
    public void apply(Stage target, StageRequest r) {
        target.setType(r.type());
        target.setStatus(r.status());
        target.setScheduledAt(r.scheduledAt());
        target.setCompletedAt(r.completedAt());
        target.setFormat(r.format());
        target.setInterviewers(r.interviewers() == null ? List.of() : List.copyOf(r.interviewers()));
        target.setNotes(r.notes());
    }

    public Compensation toEntity(CompensationRequest r) {
        if (r == null || (r.min() == null && r.max() == null && r.currency() == null)) {
            return null;
        }
        Compensation c = new Compensation();
        c.setMin(r.min());
        c.setMax(r.max());
        // Default currency when an amount is given without one (SCHEMA.md §4.2).
        c.setCurrency(r.currency() != null ? r.currency().toUpperCase(java.util.Locale.ROOT)
                : (r.min() != null || r.max() != null) ? "USD" : null);
        return c;
    }

    public List<String> normalizeTags(List<String> tags) {
        return Tags.normalize(tags);
    }
}
