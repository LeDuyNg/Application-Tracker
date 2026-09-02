import { useQuery } from '@tanstack/react-query';

import { api } from '../client';
import type { MeResponse } from '../types';
import { keys } from './keys';

/**
 * Who is logged in. Called once by the app shell on load.
 *
 * `redirectOnUnauthorized: false` is the important bit: a signed-out visitor gets a 401
 * here, and we want to show them a "Sign in" button — not immediately throw them at Google
 * before the app has even rendered. Every *other* call in the app keeps the default, so a
 * session that lapses mid-use bounces straight to re-login.
 */
export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: () => api.get<MeResponse>('/me', { redirectOnUnauthorized: false }),
    retry: false,
    staleTime: 5 * 60_000,
  });
}
