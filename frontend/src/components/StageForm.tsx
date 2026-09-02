import { useState } from 'react';

import type { StageRequest, StageResponse } from '../api/types';
import {
  STAGE_FORMATS,
  STAGE_STATUSES,
  STAGE_TYPES,
  label,
} from '../lib/enums';
import { fromDateTimeInputValue, toDateTimeInputValue } from '../lib/format';
import { ErrorNote } from './feedback';

/**
 * Add or edit one interview round. A plain controlled form (local `useState` per field) —
 * the two big entity forms use react-hook-form + zod, but a stage has few fields and no
 * cross-field rules the backend doesn't already enforce, so hand-rolling it is clearer here.
 *
 * On PATCH the backend does a *full replacement* of the stage's fields, so this form always
 * sends every field — a cleared input becomes `null`, which is the intended behaviour.
 *
 * @param existing  the stage being edited, or undefined when adding
 * @param onSubmit  fires with a complete StageRequest; the parent calls the mutation
 * @param onCancel  close without saving
 * @param pending   true while the parent's mutation is in flight
 * @param error     the parent's mutation error, if any
 */
export function StageForm({
  existing,
  onSubmit,
  onCancel,
  pending,
  error,
}: {
  existing?: StageResponse;
  onSubmit: (body: StageRequest) => void;
  onCancel: () => void;
  pending: boolean;
  error: unknown;
}) {
  const [type, setType] = useState<StageRequest['type']>(existing?.type ?? 'PHONE_SCREEN');
  const [status, setStatus] = useState<StageRequest['status']>(existing?.status ?? 'SCHEDULED');
  const [scheduledAt, setScheduledAt] = useState(toDateTimeInputValue(existing?.scheduledAt));
  const [completedAt, setCompletedAt] = useState(toDateTimeInputValue(existing?.completedAt));
  const [format, setFormat] = useState<string>(existing?.format ?? '');
  const [interviewers, setInterviewers] = useState((existing?.interviewers ?? []).join(', '));
  const [notes, setNotes] = useState(existing?.notes ?? '');

  // Mirror the two service rules so the user sees the problem before the round trip
  // (the backend still enforces them — this is just a friendlier first pass).
  const needsScheduledAt = status === 'SCHEDULED' && !scheduledAt;
  const needsCompletedAt = (status === 'PASSED' || status === 'FAILED') && !completedAt;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (needsScheduledAt || needsCompletedAt) return;
    onSubmit({
      type,
      status,
      scheduledAt: fromDateTimeInputValue(scheduledAt),
      completedAt: fromDateTimeInputValue(completedAt),
      format: format ? (format as StageRequest['format']) : null,
      interviewers: interviewers
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
      notes: notes.trim() || null,
      // sequence omitted → the service appends (add) or keeps position (edit).
    });
  }

  return (
    <form onSubmit={submit} className="card stack" style={{ background: 'var(--accent-weak)' }}>
      <div className="row-wrap">
        <label className="field" style={{ flex: '1 1 180px' }}>
          <span>Type</span>
          <select value={type} onChange={(e) => setType(e.target.value as StageRequest['type'])}>
            {STAGE_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
          </select>
        </label>

        <label className="field" style={{ flex: '1 1 160px' }}>
          <span>Status</span>
          <select value={status} onChange={(e) => setStatus(e.target.value as StageRequest['status'])}>
            {STAGE_STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
        </label>

        <label className="field" style={{ flex: '1 1 140px' }}>
          <span>Format</span>
          <select value={format} onChange={(e) => setFormat(e.target.value)}>
            <option value="">—</option>
            {STAGE_FORMATS.map((f) => <option key={f} value={f}>{label(f)}</option>)}
          </select>
        </label>
      </div>

      <div className="row-wrap">
        <label className="field" style={{ flex: '1 1 220px' }}>
          <span>Scheduled at</span>
          <input type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)} />
          {needsScheduledAt ? <span className="error">Required when status is Scheduled.</span> : null}
        </label>

        <label className="field" style={{ flex: '1 1 220px' }}>
          <span>Completed at</span>
          <input type="datetime-local" value={completedAt} onChange={(e) => setCompletedAt(e.target.value)} />
          {needsCompletedAt ? <span className="error">Required when status is Passed or Failed.</span> : null}
        </label>
      </div>

      <label className="field">
        <span>Interviewers <span className="muted small">(comma-separated)</span></span>
        <input
          value={interviewers}
          onChange={(e) => setInterviewers(e.target.value)}
          placeholder="Priya N. (Staff Eng), Alex Kim"
        />
      </label>

      <label className="field">
        <span>Notes</span>
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)} />
      </label>

      {error ? <ErrorNote error={error} /> : null}

      <div className="row">
        <button type="submit" className="primary" disabled={pending || needsScheduledAt || needsCompletedAt}>
          {pending ? 'Saving…' : existing ? 'Save round' : 'Add round'}
        </button>
        <button type="button" onClick={onCancel} disabled={pending}>Cancel</button>
      </div>
    </form>
  );
}
