/**
 * Every query key in one object, so invalidation after a mutation references the same array
 * shape the query registered with. A typo here is a cache that never updates.
 */
import type { ApplicationFilters } from './applications';

export const keys = {
  me: ['me'] as const,

  companies: ['companies'] as const,
  company: (id: string) => ['companies', id] as const,

  applications: (filters: ApplicationFilters) => ['applications', 'list', filters] as const,
  applicationLists: ['applications', 'list'] as const, // prefix — invalidates every filter combo
  application: (id: string) => ['applications', 'detail', id] as const,

  stats: (params: { days?: number; from?: string; to?: string }) => ['stats', params] as const,
  followups: ['followups'] as const,
  interviews: (days: number) => ['interviews', days] as const,
};
