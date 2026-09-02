import { useQuery } from '@tanstack/react-query';

import { api } from '../client';
import type { FollowupResponse } from '../types';
import { keys } from './keys';

/** Two lists: reminders that have come due, and active pipelines gone quiet (SCHEMA.md §10.2). */
export function useFollowups() {
  return useQuery({
    queryKey: keys.followups,
    queryFn: () => api.get<FollowupResponse>('/applications/followups'),
  });
}
