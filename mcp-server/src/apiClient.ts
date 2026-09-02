/**
 * The only place this server talks to the network.
 *
 * Mirrors the SPA's `frontend/src/api/client.ts` in spirit — one wrapper, every request
 * goes through it — but with a much smaller job, because this client is read-only.
 *
 * **`get` is the only method, and that is the design.** `CLAUDE.md §14` makes writes from
 * the MCP layer a non-goal, and the backend enforces it independently: the bearer filter
 * chain permits `GET /api/**` and denies everything else, so a POST from here would come
 * back 403 (`CLAUDE.md §6`, Phase 2). Having no way to *spell* a write in this file means
 * the rule cannot be broken here by accident, and the backend is the guarantee rather than
 * the politeness.
 */

import type { Config } from './config.js';
import type { ProblemDetail } from './types.js';

/**
 * How long to wait for the API before giving up.
 *
 * The deployed instance runs on a 1/8-OCPU host that bursts to a full core, so a warm
 * request is fast but an unlucky one is not instant. 15s is generous for a warm app and
 * still well short of a user deciding Claude has hung. Without a timeout a stalled socket
 * would hang the tool call indefinitely, and a stdio server gives no sign of life while it
 * waits.
 */
const TIMEOUT_MS = 15_000;

/**
 * A failed API call, carrying enough for the tool layer to write a useful sentence.
 *
 * `status` is 0 when the request never got a response at all (DNS, TLS, connection refused,
 * timeout) — the distinction matters, because "the server said no" and "I could not reach
 * the server" need different advice.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly problem?: ProblemDetail,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export class ApiClient {
  constructor(private readonly config: Config) {}

  /**
   * `GET {baseUrl}{path}` with the bearer token, parsed as JSON.
   *
   * @param path  an API path beginning with `/`, e.g. `/api/stats`
   * @param query parameters to append; entries whose value is `undefined` are dropped, so a
   *              caller can pass optional tool arguments straight through without building
   *              the string conditionally
   *
   * The return type is caller-asserted (`get<StatsResponse>(...)`). Nothing validates the
   * response against `types.ts` at runtime — the backend is the same codebase, its DTOs are
   * covered by integration tests, and re-validating every field here would buy little for
   * the noise. `types.ts` says what to expect; if the two ever drift, fix the mirror.
   */
  async get<T>(path: string, query?: Record<string, string | number | undefined>): Promise<T> {
    const url = new URL(this.config.baseUrl + path);
    for (const [key, value] of Object.entries(query ?? {})) {
      if (value !== undefined) {
        url.searchParams.set(key, String(value));
      }
    }

    let response: Response;
    try {
      response = await fetch(url, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${this.config.token}`,
          Accept: 'application/json',
        },
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
    } catch (cause) {
      // `fetch` rejects only when no response arrived — a 500 is a resolved promise, not a
      // rejection. So everything caught here is a transport problem.
      const reason = cause instanceof Error ? cause.message : String(cause);
      const timedOut = cause instanceof Error && cause.name === 'TimeoutError';
      throw new ApiError(
        0,
        timedOut
          ? `The API at ${this.config.baseUrl} did not respond within ${TIMEOUT_MS / 1000}s.`
          : `Could not reach the API at ${this.config.baseUrl}: ${reason}`,
      );
    }

    if (!response.ok) {
      const { message, problem } = await describeFailure(response);
      throw new ApiError(response.status, message, problem);
    }

    return (await response.json()) as T;
  }
}

/**
 * Turn a non-2xx response into a sentence worth showing.
 *
 * The backend returns RFC 7807 `application/problem+json` for every error it raises
 * (`CLAUDE.md §11`), so `detail` is usually a written explanation — "'days' must not exceed
 * 365" — that is far better than "HTTP 400". Falls back to the status line when the body is
 * not problem JSON, which is what an Nginx-level error (502, 429 from the rate limiter)
 * looks like.
 */
async function describeFailure(
  response: Response,
): Promise<{ message: string; problem?: ProblemDetail }> {
  const base = `HTTP ${response.status} ${response.statusText}`.trim();

  // The body can only be read once, so read it here and derive both results from it.
  let body: string;
  try {
    body = await response.text();
  } catch {
    return { message: base };
  }

  let problem: ProblemDetail;
  try {
    problem = JSON.parse(body) as ProblemDetail;
  } catch {
    // Not JSON. Nginx's HTML error pages land here — a 502, or a 429 from the rate
    // limiter — and a snippet is more use than nothing.
    const snippet = body.trim().slice(0, 200);
    return { message: snippet ? `${base} — ${snippet}` : base };
  }

  const parts: string[] = [];
  if (problem.detail) {
    parts.push(problem.detail);
  } else if (problem.title) {
    parts.push(problem.title);
  }
  for (const fieldError of problem.errors ?? []) {
    parts.push(`${fieldError.field}: ${fieldError.message}`);
  }

  return {
    message: parts.length > 0 ? `${base} — ${parts.join('; ')}` : base,
    problem,
  };
}
