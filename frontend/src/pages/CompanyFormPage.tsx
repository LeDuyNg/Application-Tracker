import { useNavigate, useParams } from 'react-router-dom';

import { useCompany, useCreateCompany, useUpdateCompany } from '../api/hooks/companies';
import { CompanyForm } from '../forms/CompanyForm';
import { companyToForm } from '../forms/companySchema';
import { ErrorNote, Loading } from '../components/feedback';

export function CompanyFormPage({ mode }: { mode: 'create' | 'edit' }) {
  return mode === 'create' ? <CreatePage /> : <EditPage />;
}

function CreatePage() {
  const navigate = useNavigate();
  const create = useCreateCompany();
  return (
    <div className="stack">
      <h1>New company</h1>
      <CompanyForm
        pending={create.isPending}
        error={create.error}
        submitLabel="Create company"
        onSubmit={(body) =>
          create.mutate(body, { onSuccess: (c) => navigate(`/companies/${c.id}`) })
        }
      />
    </div>
  );
}

function EditPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: company, isLoading, error } = useCompany(id);
  const update = useUpdateCompany(id ?? '');

  if (isLoading) return <Loading />;
  if (error) return <ErrorNote error={error} />;
  if (!company) return null;

  return (
    <div className="stack">
      <h1>Edit — {company.name}</h1>
      <CompanyForm
        defaultValues={companyToForm(company)}
        pending={update.isPending}
        error={update.error}
        submitLabel="Save changes"
        onSubmit={(body) =>
          update.mutate(body, { onSuccess: () => navigate(`/companies/${company.id}`) })
        }
      />
    </div>
  );
}
