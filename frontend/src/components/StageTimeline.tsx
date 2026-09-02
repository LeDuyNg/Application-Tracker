import { useState } from 'react';

import { useAddStage, useDeleteStage, useUpdateStage } from '../api/hooks/applications';
import type { StageResponse } from '../api/types';
import { label } from '../lib/enums';
import { formatDateTime } from '../lib/format';
import { StageForm } from './StageForm';
import { StageStatusBadge } from './StatusBadge';

/**
 * The vertical list of an application's rounds, with inline add / edit / delete. All three
 * mutations return the whole updated application, and the hooks push that straight into the
 * detail cache — so the timeline re-renders without a manual refetch.
 */
export function StageTimeline({ applicationId, stages }: { applicationId: string; stages: StageResponse[] }) {
  const [adding, setAdding] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  const addStage = useAddStage(applicationId);
  const updateStage = useUpdateStage(applicationId);
  const deleteStage = useDeleteStage(applicationId);

  const ordered = [...stages].sort((a, b) => a.sequence - b.sequence);

  return (
    <div className="stack">
      <div className="spread">
        <h2>Stages</h2>
        {!adding ? (
          <button type="button" onClick={() => { setAdding(true); setEditingId(null); }}>
            + Add round
          </button>
        ) : null}
      </div>

      {adding ? (
        <StageForm
          pending={addStage.isPending}
          error={addStage.error}
          onCancel={() => { setAdding(false); addStage.reset(); }}
          onSubmit={(body) =>
            addStage.mutate(body, { onSuccess: () => setAdding(false) })
          }
        />
      ) : null}

      <ol className="stack" style={{ listStyle: 'none', padding: 0, gap: 'var(--space-3)' }}>
        {ordered.map((stage) => (
          <li key={stage.stageId} className="card" style={{ padding: 'var(--space-4)' }}>
            {editingId === stage.stageId ? (
              <StageForm
                existing={stage}
                pending={updateStage.isPending}
                error={updateStage.error}
                onCancel={() => { setEditingId(null); updateStage.reset(); }}
                onSubmit={(body) =>
                  updateStage.mutate(
                    { stageId: stage.stageId, body },
                    { onSuccess: () => setEditingId(null) },
                  )
                }
              />
            ) : (
              <div className="stack" style={{ gap: 'var(--space-2)' }}>
                <div className="spread">
                  <div className="row" style={{ gap: 'var(--space-2)' }}>
                    <span className="muted mono small">#{stage.sequence}</span>
                    <strong>{label(stage.type)}</strong>
                    <StageStatusBadge status={stage.status} />
                  </div>
                  <div className="row small">
                    <button type="button" onClick={() => { setEditingId(stage.stageId); setAdding(false); }}>
                      Edit
                    </button>
                    <button
                      type="button"
                      className="danger"
                      disabled={ordered.length <= 1 || deleteStage.isPending}
                      title={ordered.length <= 1 ? 'An application must keep at least one round' : undefined}
                      onClick={() => {
                        if (confirm(`Delete the ${label(stage.type)} round?`)) {
                          deleteStage.mutate(stage.stageId);
                        }
                      }}
                    >
                      Delete
                    </button>
                  </div>
                </div>

                <div className="small muted">
                  {stage.scheduledAt ? <>Scheduled {formatDateTime(stage.scheduledAt)}</> : null}
                  {stage.scheduledAt && stage.completedAt ? ' · ' : null}
                  {stage.completedAt ? <>Completed {formatDateTime(stage.completedAt)}</> : null}
                  {stage.format ? ` · ${label(stage.format)}` : null}
                </div>

                {stage.interviewers.length > 0 ? (
                  <div className="small">With: {stage.interviewers.join(', ')}</div>
                ) : null}
                {stage.notes ? <div className="small" style={{ whiteSpace: 'pre-wrap' }}>{stage.notes}</div> : null}
              </div>
            )}
          </li>
        ))}
      </ol>

      {deleteStage.error ? (
        <p className="error-note">{(deleteStage.error as Error).message}</p>
      ) : null}
    </div>
  );
}
