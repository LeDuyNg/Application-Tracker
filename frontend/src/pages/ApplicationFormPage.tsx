import { useNavigate, useParams } from 'react-router-dom';

import {
  useApplication,
  useCreateApplication,
  useUpdateApplication,
} from '../api/hooks/applications';
import { ApplicationForm } from '../forms/ApplicationForm';
import {
  applicationToForm,
  toCreateRequest,
  toUpdateRequest,
} from '../forms/applicationSchema';
import { ErrorNote, Loading } from '../components/feedback';

/**
 * Hosts <ApplicationForm> for both `/applications/new` and `/applications/:id/edit`. The
 * page owns the mutation; the form is presentational.
 */
export function ApplicationFormPage({ mode }: { mode: 'create' | 'edit' }) {
  return mode === 'create' ? <CreatePage /> : <EditPage />;
}

function CreatePage() {
  const navigate = useNavigate();
  const create = useCreateApplication();
  return (
    <div className="stack">
      <h1>New application</h1>
      <ApplicationForm
        mode="create"
        pending={create.isPending}
        error={create.error}
        submitLabel="Create application"
        onSubmit={(values) =>
          create.mutate(toCreateRequest(values), {
            onSuccess: (app) => navigate(`/applications/${app.id}`),
          })
        }
      />
    </div>
  );
}

function EditPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: app, isLoading, error } = useApplication(id);
  const update = useUpdateApplication(id ?? '');

  if (isLoading) return <Loading />;
  if (error) return <ErrorNote error={error} />;
  if (!app) return null;

  return (
    <div className="stack">
      <h1>Edit — {app.role}</h1>
      <ApplicationForm
        mode="edit"
        defaultValues={applicationToForm(app)}
        pending={update.isPending}
        error={update.error}
        submitLabel="Save changes"
        onSubmit={(values) =>
          update.mutate(toUpdateRequest(values), {
            onSuccess: () => navigate(`/applications/${app.id}`),
          })
        }
      />
    </div>
  );
}
