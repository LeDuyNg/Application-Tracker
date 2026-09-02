/**
 * `get_upcoming_interviews` — scheduled rounds, soonest first (`SCHEMA.md §10.4`).
 */

import { z } from 'zod';
import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';

import type { ApiClient } from '../apiClient.js';
import type { UpcomingInterviewResponse } from '../types.js';
import { displayTimeZone, formatInstant, humanise, joinParts, pluralDays } from '../format.js';
import { text, toolError, type ToolResult } from './result.js';

const NAME = 'get_upcoming_interviews';

/** The API's own default, and the window "this week" means. */
const DEFAULT_DAYS = 7;

/** The API rejects anything larger; stating it here turns a 400 into a validation message. */
const MAX_DAYS = 365;

/**
 * **The result is one row per round, not per application** — an application with two rounds
 * booked this week appears twice, which is what a calendar answer needs. Saying so stops
 * Claude reporting "2 interviews at Stripe" as "2 applications at Stripe".
 */
const DESCRIPTION = `Interview rounds that are scheduled and still to come, soonest first.

Use this when the user asks what interviews they have coming up, what is on their calendar, what they need to prepare for, or what is happening this week.

"days" is how far ahead to look, counted from now: 7 for "this week", 1 for "today or tomorrow", 30 for "this month". Defaults to 7.

Each result is one interview ROUND, so a single application with two rounds booked appears twice. Only rounds that are actually scheduled are included — rounds that are expected but not yet booked have no date and do not appear, and applications that were rejected, withdrawn or accepted are excluded even if a round was left sitting on the calendar.`;

export function registerGetUpcomingInterviews(server: McpServer, api: ApiClient): void {
  server.registerTool(
    NAME,
    {
      title: 'Upcoming interviews',
      description: DESCRIPTION,
      inputSchema: {
        days: z
          .number()
          .int()
          .positive()
          .max(MAX_DAYS)
          .optional()
          .describe(`How many days ahead to look. Defaults to ${DEFAULT_DAYS}. Max ${MAX_DAYS}.`),
      },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async ({ days }): Promise<ToolResult> => {
      const window = days ?? DEFAULT_DAYS;
      try {
        const interviews = await api.get<UpcomingInterviewResponse[]>(
          '/api/applications/interviews',
          { days: window },
        );
        return text(render(window, interviews));
      } catch (cause) {
        return toolError(NAME, cause);
      }
    },
  );
}

function render(days: number, interviews: UpcomingInterviewResponse[]): string {
  if (interviews.length === 0) {
    return `No interviews scheduled in the next ${pluralDays(days)}.`;
  }

  const lines: string[] = [];
  lines.push(
    `${interviews.length} interview${interviews.length === 1 ? '' : 's'} scheduled in the next ` +
      `${pluralDays(days)} (times in ${displayTimeZone}):`,
  );

  for (const interview of interviews) {
    lines.push(`  - ${renderRow(interview)}`);
  }

  return lines.join('\n');
}

function renderRow(interview: UpcomingInterviewResponse): string {
  return joinParts(
    formatInstant(interview.scheduledAt),
    `${interview.companyName} — ${interview.role}`,
    humanise(interview.stageType),
    interview.format ? humanise(interview.format) : null,
    interview.interviewers.length > 0 ? `with ${interview.interviewers.join(', ')}` : null,
  );
}
