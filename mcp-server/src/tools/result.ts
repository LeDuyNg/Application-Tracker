/**
 * The shape every tool handler returns, and one place to turn a thrown error into it.
 *
 * An MCP tool result is `{ content: [{ type: "text", text }] }` — a list of content blocks,
 * of which we only ever use text. `isError: true` marks a failed call: Claude still sees the
 * text, but knows the tool did not answer the question and can say so rather than treating
 * an error string as data.
 */

import { ApiError } from '../apiClient.js';

export interface ToolResult {
  // The index signature is what the SDK's CallToolResult expects; without it TypeScript
  // rejects this object as too narrow when it is returned from a handler.
  [key: string]: unknown;
  content: Array<{ type: 'text'; text: string }>;
  isError?: boolean;
}

/** A successful result carrying one block of text. */
export function text(body: string): ToolResult {
  return { content: [{ type: 'text', text: body }] };
}

/**
 * A failed result, written so the sentence Claude reads suggests the actual fix.
 *
 * The status codes are not generic HTTP advice — each one has a specific meaning against
 * this API, and saying which is the difference between the user checking the right thing and
 * the user checking everything:
 *
 * - **401** — the bearer token is wrong, blank, or the server was started without one.
 *   `APP_MCP_TOKEN` on the VPS and `API_TOKEN` here must be the same string.
 * - **403** — the token is *valid* but the request was not a read. That is the read-only
 *   guarantee working as designed (`CLAUDE.md §14`), not a misconfiguration, and it should
 *   be impossible from this server since `ApiClient` only speaks GET.
 * - **429** — Nginx's rate limiter on `/api` (10r/s, burst 20). Retrying immediately makes
 *   it worse.
 * - **0** — never reached the server at all: DNS, TLS, the host being down, or the timeout.
 */
export function toolError(toolName: string, cause: unknown): ToolResult {
  const lines: string[] = [];

  if (cause instanceof ApiError) {
    lines.push(`${toolName} failed: ${cause.message}`);

    switch (cause.status) {
      case 0:
        lines.push(
          'The job tracker API could not be reached. It may be down, or this machine may be offline.',
        );
        break;
      case 401:
        lines.push(
          'The API rejected the bearer token. Check that API_TOKEN matches APP_MCP_TOKEN in ' +
            '/etc/jobtracker/jobtracker.env on the server (and that it is not blank there).',
        );
        break;
      case 403:
        lines.push(
          'The token is valid but the request was refused. The MCP token is read-only by ' +
            'design, so this should not happen for a read — worth reporting.',
        );
        break;
      case 429:
        lines.push('Rate limited by the server. Wait a few seconds before trying again.');
        break;
      default:
        break;
    }
  } else {
    const message = cause instanceof Error ? cause.message : String(cause);
    lines.push(`${toolName} failed unexpectedly: ${message}`);
  }

  // stderr, never stdout: on a stdio transport, stdout carries protocol frames and anything
  // else written there corrupts the stream (PLAN.md Phase 6).
  process.stderr.write(`[jobtracker-mcp] ${lines[0]}\n`);

  return { content: [{ type: 'text', text: lines.join('\n') }], isError: true };
}
