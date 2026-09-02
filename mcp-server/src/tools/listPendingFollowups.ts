/**
 * `list_pending_followups` — what needs chasing (`SCHEMA.md §10.2`).
 */

import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';

import type { ApiClient } from '../apiClient.js';
import type { FollowupDue, FollowupGoneQuiet, FollowupResponse } from '../types.js';
import { formatDate, formatInstantAsDate, humanise, joinParts, pluralDays } from '../format.js';
import { text, toolError, type ToolResult } from './result.js';

const NAME = 'list_pending_followups';

/**
 * **Two groups, and the description says so, because they answer different questions.**
 *
 * `due` is a reminder the owner set. `goneQuiet` is silence nobody scheduled — and it is
 * what answers "which companies haven't I heard back from in 2+ weeks?", one of the four
 * headline queries. Collapsing them into "things to follow up on" would lose the
 * distinction that makes the second one worth having (`SCHEMA.md §10.2`).
 */
const DESCRIPTION = `What needs chasing in the job search, in two separate groups.

Use this when the user asks what they should follow up on, who they need to chase, which applications need attention, or — importantly — which companies have gone quiet or have not replied in a while.

The two groups are different things and should be reported separately:
- DUE: the user set themselves a reminder date and it has arrived (or is within the next few days).
- GONE QUIET: nobody has moved this application in weeks. No reminder was set; the silence itself is the signal. This is the group that answers "which companies haven't I heard back from in 2+ weeks?".

An application can legitimately appear in both groups — that combination is the most urgent case, not a duplicate. The result states the exact day thresholds used; quote those rather than assuming a number.`;

export function registerListPendingFollowups(server: McpServer, api: ApiClient): void {
  server.registerTool(
    NAME,
    {
      title: 'Pending follow-ups',
      description: DESCRIPTION,
      // No inputSchema at all: this tool takes no arguments. The API decides the thresholds
      // and reports them back, so there is nothing for Claude to choose.
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    async (): Promise<ToolResult> => {
      try {
        const followups = await api.get<FollowupResponse>('/api/applications/followups');
        return text(render(followups));
      } catch (cause) {
        return toolError(NAME, cause);
      }
    },
  );
}

function render(followups: FollowupResponse): string {
  const { due, goneQuiet, dueWithinDays, quietAfterDays } = followups;

  if (due.length === 0 && goneQuiet.length === 0) {
    return (
      'Nothing needs chasing right now.\n' +
      `No follow-up reminders are due within ${pluralDays(dueWithinDays)}, and no active ` +
      `application has been silent for ${pluralDays(quietAfterDays)} or more.`
    );
  }

  const lines: string[] = [];

  lines.push(`Follow-ups due (reminders set, looking ahead ${pluralDays(dueWithinDays)}): ${due.length}`);
  if (due.length === 0) {
    lines.push('  none');
  } else {
    for (const item of due) {
      lines.push(`  - ${renderDue(item)}`);
    }
  }

  lines.push('');
  lines.push(
    `Gone quiet (active, no contact in ${pluralDays(quietAfterDays)}+): ${goneQuiet.length}`,
  );
  if (goneQuiet.length === 0) {
    lines.push('  none');
  } else {
    for (const item of goneQuiet) {
      lines.push(`  - ${renderQuiet(item)}`);
    }
  }

  return lines.join('\n');
}

function renderDue({ application, daysOverdue }: FollowupDue): string {
  // Negative is not a bug: the list deliberately looks a week ahead, so an upcoming
  // reminder appears before the morning it lands rather than being hidden until then.
  const timing =
    daysOverdue > 0
      ? `${pluralDays(daysOverdue)} overdue`
      : daysOverdue === 0
        ? 'due today'
        : `due in ${pluralDays(Math.abs(daysOverdue))}`;

  return joinParts(
    `${application.companyName} — ${application.role}`,
    timing,
    application.followUpDate ? `reminder ${formatDate(application.followUpDate)}` : null,
    stageAndStatus(application),
  );
}

function renderQuiet({ application, daysSinceContact }: FollowupGoneQuiet): string {
  return joinParts(
    `${application.companyName} — ${application.role}`,
    `silent ${pluralDays(daysSinceContact)}`,
    application.lastContactAt ? `last moved ${formatInstantAsDate(application.lastContactAt)}` : null,
    stageAndStatus(application),
  );
}

/** `Active · waiting on Technical interview` — the two fields that say where this stands. */
function stageAndStatus(application: FollowupDue['application']): string {
  return joinParts(
    humanise(application.status),
    application.currentStageType ? `waiting on ${humanise(application.currentStageType)}` : null,
  );
}
