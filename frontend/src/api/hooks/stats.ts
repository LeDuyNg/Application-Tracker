import { useQuery } from '@tanstack/react-query';

import { api } from '../client';
import type { StatsResponse } from '../types';
import { keys } from './keys';

/**
 * The funnel + derived rates. Pass `days` for a rolling window, or `from`/`to` for a
 * calendar range, or nothing for all-time. The backend 400s if you pass both, so the
 * dashboard only ever sends one.
 */
export function useStats(params: { days?: number; from?: string; to?: string } = {}) {
  return useQuery({
    queryKey: keys.stats(params),
    queryFn: () =>
      api.get<StatsResponse>('/stats', {
        params: { days: params.days, from: params.from, to: params.to },
      }),
  });
}
