import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '../client';
import type {
  ApplicationResponse,
  ApplicationSummaryResponse,
  CreateApplicationRequest,
  PagedModel,
  StageRequest,
  UpdateApplicationRequest,
} from '../types';
import { keys } from './keys';

/**
 * The filter bundle the list page drives. Mirrors `ApplicationSearchRequest` plus paging.
 * All optional; an empty object lists everything, newest first.
 */
export interface ApplicationFilters {
  q?: string;
  status?: string;
  companyId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export function useApplications(filters: ApplicationFilters) {
  return useQuery({
    queryKey: keys.applications(filters),
    queryFn: () =>
      api.get<PagedModel<ApplicationSummaryResponse>>('/applications', {
        params: {
          q: filters.q,
          status: filters.status,
          companyId: filters.companyId,
          from: filters.from,
          to: filters.to,
          page: filters.page,
          size: filters.size,
        },
      }),
    // Keep showing the previous page while the next one loads — no flash of an empty table.
    placeholderData: (prev) => prev,
  });
}

export function useApplication(id: string | undefined) {
  return useQuery({
    queryKey: keys.application(id ?? ''),
    queryFn: () => api.get<ApplicationResponse>(`/applications/${id}`),
    enabled: !!id,
  });
}

export function useCreateApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateApplicationRequest) =>
      api.post<ApplicationResponse>('/applications', body),
    onSuccess: () => invalidateAll(qc),
  });
}

export function useUpdateApplication(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateApplicationRequest) =>
      api.put<ApplicationResponse>(`/applications/${id}`, body),
    onSuccess: (updated) => {
      qc.setQueryData(keys.application(id), updated); // instant detail refresh
      invalidateAll(qc);
    },
  });
}

export function useDeleteApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/applications/${id}`),
    onSuccess: () => invalidateAll(qc),
  });
}

// ---- stage sub-resource. Each returns the WHOLE updated application (SCHEMA.md / controller).

export function useAddStage(applicationId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: StageRequest) =>
      api.post<ApplicationResponse>(`/applications/${applicationId}/stages`, body),
    onSuccess: (updated) => {
      qc.setQueryData(keys.application(applicationId), updated);
      invalidateAll(qc);
    },
  });
}

export function useUpdateStage(applicationId: string) {
  const qc = useQueryClient();
  return useMutation({
    // PATCH is a full replacement of the stage's fields, not a merge — a null clears a value.
    mutationFn: ({ stageId, body }: { stageId: string; body: StageRequest }) =>
      api.patch<ApplicationResponse>(`/applications/${applicationId}/stages/${stageId}`, body),
    onSuccess: (updated) => {
      qc.setQueryData(keys.application(applicationId), updated);
      invalidateAll(qc);
    },
  });
}

export function useDeleteStage(applicationId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (stageId: string) =>
      api.delete<ApplicationResponse>(`/applications/${applicationId}/stages/${stageId}`),
    onSuccess: (updated) => {
      qc.setQueryData(keys.application(applicationId), updated);
      invalidateAll(qc);
    },
  });
}

/**
 * A stage or application change can move derived fields (`status`, `currentStageType`,
 * `lastContactAt`) that feed the stats, follow-ups and upcoming-interviews views. Rather
 * than reason about which one moved, invalidate all four families — they refetch lazily and
 * only if something is mounted.
 */
function invalidateAll(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: keys.applicationLists });
  qc.invalidateQueries({ queryKey: keys.followups });
  qc.invalidateQueries({ queryKey: ['stats'] });
  qc.invalidateQueries({ queryKey: ['interviews'] });
}
