package dev.duynguyen.jobtracker.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.duynguyen.jobtracker.common.enums.StageFormat;
import dev.duynguyen.jobtracker.common.enums.StageStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;

/**
 * One round in an application's pipeline — the thing the owner calls a "process"
 * (SCHEMA.md §4.3).
 *
 * <p><strong>Embedded, not referenced.</strong> A stage is part of the application's
 * identity, the list is small and bounded (3–8 rounds), and you never read a stage without
 * its application — so one round trip renders the whole pipeline. Contrast with
 * {@code Company}, which is referenced because it has independent identity and is shared.
 */
public class Stage {

    /**
     * UUID assigned on creation. This is the stable handle for
     * {@code PATCH /api/applications/{id}/stages/{stageId}} — deliberately not the array
     * index or {@link #sequence}, both of which shift when stages are added or removed.
     */
    private String stageId;

    /**
     * 1-based order within the pipeline. Explicit rather than implied by array position,
     * because a stage can be {@code EXPECTED} long before it has a date.
     */
    private int sequence;

    private StageType type;
    private StageStatus status;

    /** Date+time in UTC. Null means "known round, not scheduled yet". */
    private Instant scheduledAt;

    /** Set when status becomes PASSED or FAILED. */
    private Instant completedAt;

    private StageFormat format;

    /** Free text, "Name (role)". Not linked to {@code Company.contacts} — interviewers are per-round. */
    private List<String> interviewers = new ArrayList<>();

    private String notes;

    public String getStageId() { return stageId; }
    public void setStageId(String stageId) { this.stageId = stageId; }

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public StageType getType() { return type; }
    public void setType(StageType type) { this.type = type; }

    public StageStatus getStatus() { return status; }
    public void setStatus(StageStatus status) { this.status = status; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public StageFormat getFormat() { return format; }
    public void setFormat(StageFormat format) { this.format = format; }

    public List<String> getInterviewers() { return interviewers; }
    public void setInterviewers(List<String> interviewers) {
        this.interviewers = interviewers == null ? new ArrayList<>() : interviewers;
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
