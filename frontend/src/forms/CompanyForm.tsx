import { useForm, useFieldArray } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

import { ErrorNote } from '../components/feedback';
import {
  companySchema,
  emptyCompanyForm,
  toCompanyRequest,
  type CompanyFormValues,
} from './companySchema';
import type { CompanyRequest } from '../api/types';

/**
 * react-hook-form keeps form state outside React's render cycle for speed. `register`
 * connects a plain <input> to it; `handleSubmit` validates (via the zod resolver) and only
 * then calls our submit fn. `useFieldArray` manages the dynamic list of contacts.
 *
 * The parent passes `onSubmit` (which calls the create/update mutation) and the mutation's
 * pending / error state — this component doesn't know about the network.
 */
export function CompanyForm({
  defaultValues = emptyCompanyForm,
  onSubmit,
  pending,
  error,
  submitLabel,
}: {
  defaultValues?: CompanyFormValues;
  onSubmit: (body: CompanyRequest) => void;
  pending: boolean;
  error: unknown;
  submitLabel: string;
}) {
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<CompanyFormValues>({
    resolver: zodResolver(companySchema),
    defaultValues,
  });

  const contacts = useFieldArray({ control, name: 'contacts' });

  return (
    <form className="stack" onSubmit={handleSubmit((v) => onSubmit(toCompanyRequest(v)))}>
      <div className="card stack">
        <label className="field">
          <span>Name *</span>
          <input {...register('name')} autoFocus />
          {errors.name ? <span className="error">{errors.name.message}</span> : null}
        </label>

        <div className="row-wrap">
          <label className="field" style={{ flex: '1 1 220px' }}>
            <span>Website</span>
            <input {...register('website')} placeholder="https://…" />
            {errors.website ? <span className="error">{errors.website.message}</span> : null}
          </label>
          <label className="field" style={{ flex: '1 1 160px' }}>
            <span>Industry</span>
            <input {...register('industry')} placeholder="Fintech" />
          </label>
          <label className="field" style={{ flex: '1 1 180px' }}>
            <span>Location</span>
            <input {...register('location')} placeholder="Remote / SF" />
          </label>
        </div>

        <label className="field">
          <span>Tags <span className="muted small">(comma-separated)</span></span>
          <input {...register('tagsText')} placeholder="fintech, high-bar" />
        </label>

        <label className="field">
          <span>Notes</span>
          <textarea {...register('notes')} />
        </label>
      </div>

      <div className="card stack">
        <div className="spread">
          <h3>Contacts</h3>
          <button
            type="button"
            onClick={() => contacts.append({ name: '', title: '', email: '', phone: '', notes: '' })}
          >
            + Add contact
          </button>
        </div>

        {contacts.fields.length === 0 ? <p className="muted small">No contacts.</p> : null}

        {contacts.fields.map((field, i) => (
          <div key={field.id} className="stack" style={{ borderTop: '1px solid var(--border)', paddingTop: 'var(--space-3)' }}>
            <div className="row-wrap">
              <label className="field" style={{ flex: '1 1 180px' }}>
                <span>Name *</span>
                <input {...register(`contacts.${i}.name`)} />
                {errors.contacts?.[i]?.name ? (
                  <span className="error">{errors.contacts[i]?.name?.message}</span>
                ) : null}
              </label>
              <label className="field" style={{ flex: '1 1 160px' }}>
                <span>Title</span>
                <input {...register(`contacts.${i}.title`)} />
              </label>
              <label className="field" style={{ flex: '1 1 200px' }}>
                <span>Email</span>
                <input {...register(`contacts.${i}.email`)} />
                {errors.contacts?.[i]?.email ? (
                  <span className="error">{errors.contacts[i]?.email?.message}</span>
                ) : null}
              </label>
              <label className="field" style={{ flex: '1 1 140px' }}>
                <span>Phone</span>
                <input {...register(`contacts.${i}.phone`)} />
              </label>
            </div>
            <label className="field">
              <span>Contact notes</span>
              <input {...register(`contacts.${i}.notes`)} />
            </label>
            <div>
              <button type="button" className="danger" onClick={() => contacts.remove(i)}>
                Remove contact
              </button>
            </div>
          </div>
        ))}
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
