/**
 * The one place the app talks to the network.
 *
 * Everything else — every hook in `hooks/` — goes through `api.get` / `api.post` / etc.
 * Components never call `fetch` directly (CLAUDE.md §11). Centralising it means the session
 * cookie, the CSRF header, error shape and the "your session expired" redirect are all
 * handled once.
 */

import type { PagedModel } from './types';

/** Same-origin: the Vite proxy forwards `/api` to the backend on :8080. */
const BASE = '/api';

/**
 * A non-2xx response, thrown so React Query treats it as an error. Carries the parsed
 * RFC 7807 ProblemDetail body when the server sent one, so forms can show field messages.
 */
export class ApiError extends Error {
  readonly status: number;
  /** The `detail` string from the ProblemDetail body, if any. */
  readonly detail: string | undefined;
  /** The `errors: [{field, message}]` array attached to validation failures, if any. */
  readonly fieldErrors: Array<{ field: string; message: string }>;

  constructor(status: number, message: string, detail?: string, fieldErrors?: Array<{ field: string; message: string }>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.detail = detail;
    this.fieldErrors = fieldErrors ?? [];
  }
}

/** Read a cookie by name. Used for the CSRF token Spring drops as `XSRF-TOKEN`. */
function readCookie(name: string): string | undefined {
  const hit = document.cookie
    .split('; ')
    .find((row) => row.startsWith(name + '='));
  return hit ? decodeURIComponent(hit.slice(name.length + 1)) : undefined;
}

interface RequestOptions {
  /** Query-string params. `undefined` / `null` values are dropped. */
  params?: Record<string, string | number | boolean | undefined | null>;
  /**
   * On a 401, send the browser to Google login instead of throwing. Default `true` — a
   * session that expired mid-use should just re-authenticate. `useMe()` sets this `false`
   * so the app shell can render a "Sign in" button rather than bouncing on first load.
   */
  redirectOnUnauthorized?: boolean;
}

const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const url = new URL(BASE + path, window.location.origin);
  for (const [key, value] of Object.entries(options.params ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value));
    }
  }

  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  // CSRF: Spring's CookieCsrfTokenRepository sets a readable `XSRF-TOKEN` cookie and
  // expects it echoed back in the `X-XSRF-TOKEN` header on any state-changing request.
  if (MUTATING.has(method)) {
    const token = readCookie('XSRF-TOKEN');
    if (token) headers['X-XSRF-TOKEN'] = token;
  }

  const response = await fetch(url, {
    method,
    headers,
    // Send and accept the session cookie even though this is technically a fetch to the
    // same origin — explicit is safer, and it is required if the proxy is ever dropped.
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && (options.redirectOnUnauthorized ?? true)) {
    window.location.href = '/oauth2/authorization/google';
    // Never resolves — the navigation replaces the page.
    return new Promise<T>(() => {});
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const isJson = response.headers.get('content-type')?.includes('json');
  const payload = isJson ? await response.json() : await response.text();

  if (!response.ok) {
    // RFC 7807 body: { type, title, status, detail, errors? }
    const detail = typeof payload === 'object' && payload && 'detail' in payload
      ? String(payload.detail)
      : undefined;
    const fieldErrors = typeof payload === 'object' && payload && Array.isArray(payload.errors)
      ? (payload.errors as Array<{ field: string; message: string }>)
      : undefined;
    const title = typeof payload === 'object' && payload && 'title' in payload
      ? String(payload.title)
      : response.statusText;
    throw new ApiError(response.status, detail ?? title, detail, fieldErrors);
  }

  return payload as T;
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>('GET', path, undefined, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>('POST', path, body, options),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>('PUT', path, body, options),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>('PATCH', path, body, options),
  delete: <T>(path: string, options?: RequestOptions) => request<T>('DELETE', path, undefined, options),
};

/** Narrow helper the applications list uses to page through `PagedModel` results. */
export type { PagedModel };
