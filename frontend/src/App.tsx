import { Navigate, Route, Routes } from 'react-router-dom';

import { AppShell } from './components/AppShell';
import { Dashboard } from './pages/Dashboard';
import { ApplicationsList } from './pages/ApplicationsList';
import { ApplicationDetail } from './pages/ApplicationDetail';
import { ApplicationFormPage } from './pages/ApplicationFormPage';
import { CompaniesList } from './pages/CompaniesList';
import { CompanyDetail } from './pages/CompanyDetail';
import { CompanyFormPage } from './pages/CompanyFormPage';

/**
 * The route table. <AppShell> is the persistent frame (nav bar + signed-in email); the
 * nested <Route>s render into its <Outlet>. Client-side: changing the URL swaps the inner
 * component without reloading the page.
 *
 * `:id` segments are route params, read with `useParams()` in the page component.
 */
export default function App() {
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

        {/* Anything else → home. */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
