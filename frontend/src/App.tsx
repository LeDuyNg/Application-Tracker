import { Navigate, Route, Routes } from 'react-router-dom';

import { useMe } from './api/hooks/useMe';
import { AppShell } from './components/AppShell';
import { Splash } from './components/feedback';
import { Landing } from './pages/Landing';
import { Dashboard } from './pages/Dashboard';
import { ApplicationsList } from './pages/ApplicationsList';
import { ApplicationDetail } from './pages/ApplicationDetail';
import { ApplicationFormPage } from './pages/ApplicationFormPage';
import { CompaniesList } from './pages/CompaniesList';
import { CompanyDetail } from './pages/CompanyDetail';
import { CompanyFormPage } from './pages/CompanyFormPage';

/**
 * Auth gate + route table.
 *
 * `useMe()` runs once here. Until it resolves we show a splash; if it reports no
 * signed-in person we show the <Landing> page (and never mount the router, so none of the
 * data hooks fire and nobody gets bounced to Google before seeing anything); otherwise the
 * full app renders.
 *
 * <AppShell> is the persistent frame (header + nav); the nested <Route>s render into its
 * <Outlet>. `:id` segments are route params, read with `useParams()` in the page.
 */
export default function App() {
  const me = useMe();

  if (me.isLoading) return <Splash />;
  if (!me.data?.person) return <Landing />;

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<Dashboard />} />

        <Route path="/applications" element={<ApplicationsList />} />
        <Route path="/applications/new" element={<ApplicationFormPage mode="create" />} />
        <Route path="/applications/:id" element={<ApplicationDetail />} />
        <Route path="/applications/:id/edit" element={<ApplicationFormPage mode="edit" />} />

        <Route path="/companies" element={<CompaniesList />} />
        <Route path="/companies/new" element={<CompanyFormPage mode="create" />} />
        <Route path="/companies/:id" element={<CompanyDetail />} />
        <Route path="/companies/:id/edit" element={<CompanyFormPage mode="edit" />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
