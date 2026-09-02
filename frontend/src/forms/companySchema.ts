import { z } from 'zod';

import type { CompanyRequest } from '../api/types';
import { safeUrl } from '../lib/url';

/**
 * zod is a runtime schema validator. `companySchema.parse(x)` either returns a typed value
 * or throws with per-field messages; react-hook-form's zodResolver wires those messages
 * onto the form fields. The schema mirrors `CreateCompanyRequest` / `UpdateCompanyRequest`
 * on the backend (SCHEMA.md §8.1) — the backend re-validates, this is the fast feedback.
 *
 * The schema is written over the *form's* shape: every text field is a string (an empty
 * one means "not filled in"), contacts and tags are arrays. `toCompanyRequest` below maps
 * that to the wire DTO, turning blanks into `null`.
 */
const contactSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(120),
  title: z.string().trim().max(120).optional(),
  email: z
    .string()
    .trim()
    .max(254)
    .optional()
    .refine((v) => !v || z.string().email().safeParse(v).success, 'Not a valid email'),
  phone: z.string().trim().max(40).optional(),
  notes: z.string().trim().max(2000).optional(),
});

export const companySchema = z.object({
  name: z.string().trim().min(1, 'Company name is required').max(120),
  // Blank is fine (means "not filled in"); anything else must be a link the detail
  // page will actually open. Mirrors the backend's Validation.HTTP_URL so a bad value
  // is caught in the form rather than coming back as a 400.
  website: z
    .string()
    .trim()
    .max(500)
    .optional()
    .refine((v) => !v || safeUrl(v) !== null, 'Must start with http:// or https://'),
  industry: z.string().trim().max(120).optional(),
  location: z.string().trim().max(200).optional(),
  contacts: z.array(contactSchema),
  notes: z.string().trim().max(5000).optional(),
  /** One free-text box in the UI; split to an array on submit. */
  tagsText: z.string().max(400).optional(),
});

export type CompanyFormValues = z.infer<typeof companySchema>;

export const emptyCompanyForm: CompanyFormValues = {
  name: '',
  website: '',
  industry: '',
  location: '',
  contacts: [],
  notes: '',
  tagsText: '',
};

/** "" → undefined, so the backend stores null rather than an empty string. */
function blankToUndef(v: string | undefined): string | undefined {
  const t = v?.trim();
  return t ? t : undefined;
}

export function toCompanyRequest(values: CompanyFormValues): CompanyRequest {
  return {
    name: values.name.trim(),
    website: blankToUndef(values.website),
    industry: blankToUndef(values.industry),
    location: blankToUndef(values.location),
    notes: blankToUndef(values.notes),
    tags: (values.tagsText ?? '')
      .split(',')
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean),
    contacts: values.contacts.map((c) => ({
      name: c.name.trim(),
      title: blankToUndef(c.title),
      email: blankToUndef(c.email),
      phone: blankToUndef(c.phone),
      notes: blankToUndef(c.notes),
    })),
  };
}

/** Prefill the form when editing an existing company. */
export function companyToForm(c: {
  name: string;
  website: string | null;
  industry: string | null;
  location: string | null;
  notes: string | null;
  tags: string[];
  contacts: Array<{
    name: string;
    title: string | null;
    email: string | null;
    phone: string | null;
    notes: string | null;
  }>;
}): CompanyFormValues {
  return {
    name: c.name,
    website: c.website ?? '',
    industry: c.industry ?? '',
    location: c.location ?? '',
    notes: c.notes ?? '',
    tagsText: c.tags.join(', '),
    contacts: c.contacts.map((ct) => ({
      name: ct.name,
      title: ct.title ?? '',
      email: ct.email ?? '',
      phone: ct.phone ?? '',
      notes: ct.notes ?? '',
    })),
  };
}
