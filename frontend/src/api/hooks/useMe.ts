import { useMutation, useQuery } from '@tanstack/react-query';

import { api } from '../client';
import { queryClient } from '../queryClient';
import type { MeResponse } from '../types';
import { keys } from './keys';

/**
 * Who is logged in. Called once by <App> to decide between the landing page and the app.
 *
 * `redirectOnUnauthorized: false` is the important bit: a signed-out visitor gets a 401
 * here, and we want to show them the landing page — not immediately throw them at Google
 * before anything renders. Every *other* call in the app keeps the default, so a session
 * that lapses mid-use bounces straight to re-login.
 */
export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: () => api.get<MeResponse>('/me', { redirectOnUnauthorized: false }),
    retry: false,
    staleTime: 5 * 60_000,
  });
}

/**
 * Sign out. POSTs to Spring Security's /logout (CSRF-protected — the api client attaches
 * the header), which returns 204 and clears the session cookie. Then we wipe the client
 * cache and hard-navigate to "/", so the app re-mounts with no stale data and <App> falls
 * through to the landing page.
 */
export function useLogout() {
  return useMutation({
    mutationFn: () => api.post<void>('/logout'),
    onSuccess: () => {
      queryClient.clear();
      window.location.assign('/');
    },
  });
}
