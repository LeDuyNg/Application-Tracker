import { useQuery } from '@tanstack/react-query';

import { api } from '../client';
import type { UpcomingInterviewResponse } from '../types';
import { keys } from './keys';

/** Scheduled rounds in the next N days, one row per round, soonest first. `days` caps at 365. */
export function useUpcomingInterviews(days = 7) {
  return useQuery({
    queryKey: keys.interviews(days),
    queryFn: () =>
      api.get<UpcomingInterviewResponse[]>('/applications/interviews', { params: { days } }),
  });
}
