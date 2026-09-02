/**
 * `get_application_stats` — the funnel and the derived rates (`SCHEMA.md §9`, §10.1).
 */

import { z } from 'zod';
import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';

import type { ApiClient } from '../apiClient.js';
import type { StatsResponse } from '../types.js';
import { formatDate, formatPct, humanise, pluralDays } from '../format.js';
import { text, toolError, type ToolResult } from './result.js';

const NAME = 'get_application_stats';

/**
 * **The window wording is the load-bearing part of this description.**
 *
 * "How many applications did I send this month?" is one of the four headline queries, and it
 * means a *calendar* month. `days=30` is a rolling window and cannot express it — on 5
 * September a rolling 30 days reaches back into August, so answering with it and calling it
 * "this month" is a wrong number stated confidently. The API takes both for exactly this
 * reason (`SCHEMA.md §10.1`), so the description has to tell Claude which to reach for.
 */
const DESCRIPTION = `Job-search statistics: how many applications, how they are progressing, and the derived rates.

Use this when the user asks how many applications they have sent, how their search is going, what their response or offer rate is, how far applications are getting, or how much is still in play.

Choosing the window — this matters, the two are not interchangeable:
- A CALENDAR period ("this month", "in August", "so far this year", "between two dates") -> pass "from" and "to" as YYYY-MM-DD dates. "This month" means the 1st of the current month to today.
- A ROLLING look-back ("the last 30 days", "recently", "the past two weeks") -> pass "days".
- The whole search, all time -> pass nothing.
Passing both "days" and "from"/"to" is rejected.

The result states the window it actually measured. Quote that window when reporting the numbers, and never describe a rolling window as a calendar one.`;

export function registerGetApplicationStats(server: McpServer, api: ApiClient): void {
  server.registerTool(
    NAME,
    {
      title: 'Application statistics',
      description: DESCRIPTION,
      // `inputSchema` is a plain object of zod validators — a "raw shape", not a
      // `z.object(...)`. The SDK wraps it and converts it to the JSON Schema that Claude
      // sees, so the `.describe()` text below reaches Claude as the parameter documentation.
      inputSchema: {
        days: z
          .number()
          .int()
          .positive()
          .optional()
          .describe('Rolling look-back in days. Mutually exclusive with from/to.'),
        from: z
          .string()
          .regex(/^\d{4}-\d{2}-\d{2}$/, 'must be YYYY-MM-DD')
          .optional()
          .describe('Start of a calendar range, YYYY-MM-DD. Use with "to".'),
        to: z
          .string()
          .regex(/^\d{4}-\d{2}-\d{2}$/, 'must be YYYY-MM-DD')
          .optional()
          .describe('End of a calendar range, YYYY-MM-DD, inclusive. Use with "from".'),
      },
      // Advertised to the client: this tool only reads, and it reaches a system outside
      // this machine. `CLAUDE.md §14` makes read-only a project-level guarantee; the
      // backend's bearer chain is what actually enforces it.
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async ({ days, from, to }): Promise<ToolResult> => {
      // Cross-field rules cannot live in a raw shape, so they are checked here. The backend
      // raises its own 400 for the same case; catching it first turns a round trip into an
      // immediate, more specific sentence.
      if (days !== undefined && (from !== undefined || to !== undefined)) {
        return text(
          'Ask for either a rolling window ("days") or a calendar range ("from"/"to"), not both. ' +
            'For "this month" use from/to.',
        );
      }
      if ((from === undefined) !== (to === undefined)) {
        return text('A calendar range needs both "from" and "to".');
      }

      try {
        const stats = await api.get<StatsResponse>('/api/stats', { days, from, to });
        return text(render(stats));
      } catch (cause) {
        return toolError(NAME, cause);
      }
    },
  );
}

function render(stats: StatsResponse): string {
  const lines: string[] = [];

  // The window first, and always — every number below is only meaningful with it attached.
  lines.push(`Window: ${describeWindow(stats)}`);
  lines.push(`Total applications: ${stats.totalApplications}`);

  if (stats.totalApplications === 0) {
    lines.push('');
    lines.push('No applications fall in this window.');
    return lines.join('\n');
  }

  lines.push(`Still active: ${stats.activePipeline}`);
  lines.push('');

  const byStatus = Object.entries(stats.byStatus);
  if (byStatus.length > 0) {
    lines.push('By status:');
    for (const [status, count] of byStatus) {
      lines.push(`  ${humanise(status)}: ${count}`);
    }
    lines.push('');
  }

  if (stats.funnel.length > 0) {
    lines.push('Funnel — how far each application got (furthest stage passed):');
    for (const entry of stats.funnel) {
      lines.push(`  ${humanise(entry.stageType)}: ${entry.count}`);
    }
    lines.push('');
  }

  lines.push('Rates:');
  lines.push(`  Response rate: ${formatPct(stats.responseRatePct)} (got past the initial submission)`);
  lines.push(`  Offer rate: ${formatPct(stats.offerRatePct)}`);
  lines.push(
    `  Ghost rate: ${formatPct(stats.ghostRatePct)} ` +
      '(no reply, of applications old enough to count as ghosted)',
  );
  lines.push(
    stats.avgDaysToFirstResponse === null
      ? '  Average time to first response: no responses yet'
      : `  Average time to first response: ${pluralDays(
          Math.round(stats.avgDaysToFirstResponse * 10) / 10,
        )}`,
  );

  return lines.join('\n');
}

/**
 * Prefer the backend's own `description` — it is written by the code that applied the
 * filter, so it cannot disagree with what was measured. The dates are appended when present
 * so the answer is quotable without a second call.
 */
function describeWindow(stats: StatsResponse): string {
  const { from, to, description } = stats.window;
  if (from && to) {
    return `${description} (${formatDate(from)} to ${formatDate(to)})`;
  }
  return description;
}
