import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

import { useCompanies } from '../api/hooks/companies';
import { ErrorNote } from '../components/feedback';
import {
  APPLICATION_SOURCES,
  APPLICATION_STATUSES,
  WORK_MODES,
  label,
} from '../lib/enums';
import {
  applicationSchema,
  emptyApplicationForm,
  type ApplicationFormValues,
} from './applicationSchema';

/**
 * Create / edit an application's own fields. `mode` only changes the company <select>
 * (locked once created is not required, but the status field is hidden on create since the
 * backend defaults it to ACTIVE and a brand-new application is essentially always ACTIVE).
 *
 * The parent maps the returned values to the right DTO (`toCreateRequest` / `toUpdateRequest`)
 * and fires the mutation.
 */
export function ApplicationForm({
  mode,
  defaultValues = emptyApplicationForm,
  onSubmit,
  pending,
  error,
  submitLabel,
}: {
  mode: 'create' | 'edit';
  defaultValues?: ApplicationFormValues;
  onSubmit: (values: ApplicationFormValues) => void;
  pending: boolean;
  error: unknown;
  submitLabel: string;
}) {
  const companies = useCompanies();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ApplicationFormValues>({
    resolver: zodResolver(applicationSchema),
    defaultValues,
  });

  return (
    <form className="stack" onSubmit={handleSubmit(onSubmit)}>
      <div className="card stack">
        <label className="field">
          <span>Company *</span>
          <div className="row" style={{ gap: 'var(--space-2)' }}>
            <select {...register('companyId')} style={{ flex: 1 }}>
              <option value="">— choose —</option>
              {(companies.data ?? []).map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
            <Link to="/companies/new" className="small nowrap">+ New company</Link>
          </div>
          {errors.companyId ? <span className="error">{errors.companyId.message}</span> : null}
        </label>

        <label className="field">
          <span>Role *</span>
          <input {...register('role')} placeholder="Backend Engineer, Payments" />
          {errors.role ? <span className="error">{errors.role.message}</span> : null}
        </label>

        <div className="row-wrap">
          <label className="field" style={{ flex: '1 1 160px' }}>
            <span>Applied date *</span>
            <input type="date" {...register('appliedDate')} />
            {errors.appliedDate ? <span className="error">{errors.appliedDate.message}</span> : null}
          </label>

          <label className="field" style={{ flex: '1 1 180px' }}>
            <span>Source *</span>
            <select {...register('source')}>
              {APPLICATION_SOURCES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
            </select>
          </label>

          {mode === 'edit' ? (
            <label className="field" style={{ flex: '1 1 160px' }}>
              <span>Status</span>
              <select {...register('status')}>
                {APPLICATION_STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
              </select>
            </label>
          ) : null}

          <label className="field" style={{ flex: '1 1 160px' }}>
            <span>Follow-up date</span>
            <input type="date" {...register('followUpDate')} />
          </label>
        </div>
      </div>

      <div className="card stack">
        <h3>Details</h3>
        <div className="row-wrap">
          <label className="field" style={{ flex: '1 1 160px' }}>
            <span>Work mode</span>
            <select {...register('workMode')}>
              <option value="">—</option>
              {WORK_MODES.map((w) => <option key={w} value={w}>{label(w)}</option>)}
            </select>
          </label>
          <label className="field" style={{ flex: '2 1 240px' }}>
            <span>Location</span>
            <input {...register('location')} placeholder="Remote (US)" />
          </label>
        </div>

        <label className="field">
          <span>Job posting URL</span>
          <input {...register('jobPostingUrl')} placeholder="https://…" />
          {errors.jobPostingUrl ? <span className="error">{errors.jobPostingUrl.message}</span> : null}
        </label>

        <div className="row-wrap">
          <label className="field" style={{ flex: '1 1 120px' }}>
            <span>Comp min</span>
            <input type="number" {...register('compMin')} placeholder="150000" />
          </label>
          <label className="field" style={{ flex: '1 1 120px' }}>
            <span>Comp max</span>
            <input type="number" {...register('compMax')} placeholder="185000" />
            {errors.compMax ? <span className="error">{errors.compMax.message}</span> : null}
          </label>
          <label className="field" style={{ flex: '1 1 90px' }}>
            <span>Currency</span>
            <input {...register('compCurrency')} placeholder="USD" />
          </label>
        </div>

        <label className="field">
          <span>Tags <span className="muted small">(comma-separated)</span></span>
          <input {...register('tagsText')} placeholder="referral, payments" />
        </label>

        <label className="field">
          <span>Notes</span>
          <textarea {...register('notes')} rows={4} />
        </label>
      </div>

      {error ? <ErrorNote error={error} /> : null}

      <div className="row">
        <button type="submit" className="primary" disabled={pending}>
          {pending ? 'Saving…' : submitLabel}
        </button>
      </div>
    </form>
  );
}
