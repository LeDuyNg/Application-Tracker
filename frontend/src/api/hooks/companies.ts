import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '../client';
import type { CompanyRequest, CompanyResponse } from '../types';
import { keys } from './keys';

/** All companies, alphabetical (the backend sorts). Used by selects and the companies page. */
export function useCompanies() {
  return useQuery({
    queryKey: keys.companies,
    queryFn: () => api.get<CompanyResponse[]>('/companies'),
  });
}

export function useCompany(id: string | undefined) {
  return useQuery({
    queryKey: keys.company(id ?? ''),
    queryFn: () => api.get<CompanyResponse>(`/companies/${id}`),
    enabled: !!id, // don't fire until we actually have an id (e.g. route param still loading)
  });
}

export function useCreateCompany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CompanyRequest) => api.post<CompanyResponse>('/companies', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.companies }),
  });
}

export function useUpdateCompany(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CompanyRequest) => api.put<CompanyResponse>(`/companies/${id}`, body),
    onSuccess: () => {
      // A rename cascades to every application's denormalized companyName, so lists too.
      qc.invalidateQueries({ queryKey: keys.companies });
      qc.invalidateQueries({ queryKey: keys.company(id) });
      qc.invalidateQueries({ queryKey: keys.applicationLists });
    },
  });
}

export function useDeleteCompany() {
  const qc = useQueryClient();
  return useMutation({
    // 409 if applications still reference it — the ApiError surfaces the backend's message.
    mutationFn: (id: string) => api.delete<void>(`/companies/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.companies }),
  });
}
