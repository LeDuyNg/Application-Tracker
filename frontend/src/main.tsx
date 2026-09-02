import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';

import App from './App';
import { queryClient } from './api/queryClient';
import './styles/theme.css';

/**
 * App entry point. Two providers wrap everything:
 *
 *  - <QueryClientProvider> makes the shared TanStack Query cache available to every hook.
 *  - <BrowserRouter> enables client-side routing (URL changes without a full page reload).
 *
 * <StrictMode> is a dev-only helper that double-invokes some functions to surface bugs; it
 * does nothing in the production build.
 */
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
