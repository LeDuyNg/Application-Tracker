import { z } from 'zod';

import type {
  ApplicationResponse,
  CreateApplicationRequest,
  UpdateApplicationRequest,
} from '../api/types';
import {
  APPLICATION_SOURCES,
  APPLICATION_STATUSES,
  WORK_MODES,
} from '../lib/enums';

/**
 * Mirrors `CreateApplicationRequest` / `UpdateApplicationRequest` (SCHEMA.md §8.1).
 *
 * Stages are deliberately NOT here — rounds are managed through the stage sub-resource on
 * the detail page, so exactly one code path maintains the derived fields. On create the
 * backend seeds an APPLICATION_SUBMITTED stage for you.
 *
 * Written over the form shape (strings + a nested compensation object of strings); mapped
 * to the wire DTO by `toCreateRequest` / `toUpdateRequest`.
 */
const enumOptions = <T extends string>(values: readonly T[]) =>
  z.string().refine((v) => (values as readonly string[]).includes(v), 'Pick one');

export const applicationSchema = z
  .object({
    companyId: z.string().min(1, 'Choose a company'),
    role: z.string().trim().min(1, 'Role is required').max(160),
    status: enumOptions(APPLICATION_STATUSES),
    appliedDate: z.string().min(1, 'Applied date is required'),
    source: enumOptions(APPLICATION_SOURCES),
    followUpDate: z.string().optional(),
    jobPostingUrl: z.string().trim().max(1000).optional(),
    location: z.string().trim().max(200).optional(),
    workMode: z.string().optional(), // '' or a WorkMode
    compMin: z.string().optional(),
    compMax: z.string().optional(),
    compCurrency: z.string().trim().max(3).optional(),
    notes: z.string().trim().max(10000).optional(),
    tagsText: z.string().max(400).optional(),
  })
  .refine((v) => !v.appliedDate || v.appliedDate <= today(), {
    path: ['appliedDate'],
    message: 'Applied date cannot be in the future',
  })
  .refine(
    (v) => {
      const min = numOrNull(v.compMin);
      const max = numOrNull(v.compMax);
      return min == null || max == null || max >= min;
    },
    { path: ['compMax'], message: 'Max must be ≥ min' },
  )
  .refine((v) => v.workMode === undefined || v.workMode === '' || (WORK_MODES as string[]).includes(v.workMode), {
    path: ['workMode'],
    message: 'Pick one',
  });

export type ApplicationFormValues = z.infer<typeof applicationSchema>;

/** yyyy-MM-dd in local time — matches what <input type="date"> produces. */
function today(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

function numOrNull(v: string | undefined): number | null {
  if (v == null || v.trim() === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function blankToUndef(v: string | undefined): string | undefined {
  const t = v?.trim();
  return t ? t : undefined;
}

export const emptyApplicationForm: ApplicationFormValues = {
  companyId: '',
  role: '',
  status: 'ACTIVE',
  appliedDate: today(),
  source: 'COLD_APPLY',
  followUpDate: '',
  jobPostingUrl: '',
  location: '',
  workMode: '',
  compMin: '',
  compMax: '',
  compCurrency: '',
  notes: '',
  tagsText: '',
};

function sharedFields(v: ApplicationFormValues) {
  const min = numOrNull(v.compMin);
  const max = numOrNull(v.compMax);
  const currency = blankToUndef(v.compCurrency)?.toUpperCase();
  const compensation =
    min == null && max == null && !currency ? null : { min, max, currency: currency ?? 'USD' };

  return {
    companyId: v.companyId,
    role: v.role.trim(),
    appliedDate: v.appliedDate,
    source: v.source as CreateApplicationRequest['source'],
    followUpDate: blankToUndef(v.followUpDate) ?? null,
    jobPostingUrl: blankToUndef(v.jobPostingUrl) ?? null,
    location: blankToUndef(v.location) ?? null,
    workMode: (blankToUndef(v.workMode) as CreateApplicationRequest['workMode']) ?? null,
    compensation,
    notes: blankToUndef(v.notes) ?? null,
    tags: (v.tagsText ?? '')
      .split(',')
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean),
  };
}

export function toCreateRequest(v: ApplicationFormValues): CreateApplicationRequest {
  return {
    ...sharedFields(v),
    status: v.status as CreateApplicationRequest['status'],
  };
}

export function toUpdateRequest(v: ApplicationFormValues): UpdateApplicationRequest {
  return {
    ...sharedFields(v),
    status: v.status as UpdateApplicationRequest['status'],
  };
}

export function applicationToForm(a: ApplicationResponse): ApplicationFormValues {
  return {
    companyId: a.companyId,
    role: a.role,
    status: a.status,
    appliedDate: a.appliedDate.slice(0, 10),
    source: a.source,
    followUpDate: a.followUpDate?.slice(0, 10) ?? '',
    jobPostingUrl: a.jobPostingUrl ?? '',
    location: a.location ?? '',
    workMode: a.workMode ?? '',
    compMin: a.compensation?.min?.toString() ?? '',
    compMax: a.compensation?.max?.toString() ?? '',
    compCurrency: a.compensation?.currency ?? '',
    notes: a.notes ?? '',
    tagsText: a.tags.join(', '),
  };
}
