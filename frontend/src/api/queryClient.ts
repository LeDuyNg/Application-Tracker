import { QueryClient } from '@tanstack/react-query';

/**
 * TanStack Query keeps a cache of server data keyed by a "query key". A component asks for
 * data by key; if the cache has a fresh copy it renders immediately, otherwise the query
 * function runs. After a mutation we call `queryClient.invalidateQueries` to mark the
 * affected keys stale so they refetch. This is the whole reason we don't hand-roll loading
 * / error / refetch state in every component (CLAUDE.md §3, PLAN.md Phase 3).
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Treat data as fresh for 30s — avoids a refetch storm when several components
      // mount at once on the dashboard.
      staleTime: 30_000,
      // A 401 already redirects inside the api client; other errors are shown, not retried
      // into a wall.
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});
