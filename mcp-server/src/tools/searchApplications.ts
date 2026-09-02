/**
 * `search_applications` — free-text search over the job search (`SCHEMA.md §10.3`).
 */

import { z } from 'zod';
import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';

import type { ApiClient } from '../apiClient.js';
import type { ApplicationSummaryResponse, PagedModel } from '../types.js';
import { formatDate, formatInstantAsDate, humanise, joinParts } from '../format.js';
import { text, toolError, type ToolResult } from './result.js';

const NAME = 'search_applications';

/**
 * How many matches to return.
 *
 * The endpoint pages and caps `size` at 100, but the constraint here is the context window,
 * not the API: every row Claude reads costs tokens. 15 is enough to answer "everything
 * related to Stripe" completely at this project's scale, and the footer says when there are
 * more so the count is never silently wrong.
 */
const LIMIT = 15;

/**
 * **"Partial words work" is worth telling Claude explicitly.**
 *
 * The backend searches with an escaped case-insensitive regex rather than a `$text` index,
 * precisely so `strip` finds Stripe and `eng` finds Engineer (`SCHEMA.md §6`). Claude will
 * otherwise assume whole-word matching and pre-emptively "correct" the user's term, or give
 * up on a partial one.
 */
const DESCRIPTION = `Search applications by company name, role title, or notes.

Use this when the user asks about a specific company or role, or wants to find applications matching a word or phrase — "everything related to Stripe", "my backend roles", "which fintech applications do I have".

Matching is case-insensitive and matches PARTIAL words, so "strip" finds Stripe and "eng" finds Engineer. Pass the user's term as they said it; there is no need to guess the full spelling or the exact job title.

Returns the most recent matches with company, role, status, the round currently in play, and when it was applied to. It does not return notes or interview details — for the full history of one application, this gives you the company and role to ask about.`;

export function registerSearchApplications(server: McpServer, api: ApiClient): void {
  server.registerTool(
    NAME,
    {
      title: 'Search applications',
      description: DESCRIPTION,
      inputSchema: {
        query: z
          .string()
          .min(1, 'query must not be empty')
          .describe(
            'The text to search for, e.g. "Stripe" or "backend". Partial words match.',
          ),
      },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async ({ query }): Promise<ToolResult> => {
      try {
        // `q` goes straight through. The backend escapes it with Pattern.quote before
        // building the regex, so punctuation is matched literally rather than executed —
        // an unescaped `.*` there would quietly return the whole collection
        // (`SCHEMA.md §10.3`). Escaping here as well would double it and break the search.
        const page = await api.get<PagedModel<ApplicationSummaryResponse>>('/api/applications', {
          q: query,
          size: LIMIT,
        });
        return text(render(query, page));
      } catch (cause) {
        return toolError(NAME, cause);
      }
    },
  );
}

function render(query: string, page: PagedModel<ApplicationSummaryResponse>): string {
  const total = page.page.totalElements;

  if (total === 0) {
    return `No applications match "${query}".`;
  }

  const lines: string[] = [];
  const shown = page.content.length;

  lines.push(
    shown < total
      ? `${total} applications match "${query}" — showing the ${shown} most recent:`
      : `${total} application${total === 1 ? '' : 's'} match${total === 1 ? 'es' : ''} "${query}":`,
  );

  for (const application of page.content) {
    lines.push(`  - ${renderRow(application)}`);
  }

  if (shown < total) {
    lines.push('');
    lines.push(`(${total - shown} further matches not shown. Narrow the search to see them.)`);
  }

  return lines.join('\n');
}

function renderRow(application: ApplicationSummaryResponse): string {
  return joinParts(
    `${application.companyName} — ${application.role}`,
    humanise(application.status),
    application.currentStageType ? `at ${humanise(application.currentStageType)}` : null,
    `applied ${formatDate(application.appliedDate)}`,
    application.lastContactAt
      ? `last moved ${formatInstantAsDate(application.lastContactAt)}`
      : null,
  );
}
