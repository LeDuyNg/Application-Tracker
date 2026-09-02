/**
 * The MCP server: four read-only tools over the deployed Job Application Tracker API.
 *
 * **What MCP is, in one paragraph.** The Model Context Protocol lets an LLM client — here,
 * Claude Desktop — discover and call *tools* you define. Claude Desktop launches this file
 * as a child process and talks to it over the process's own stdin and stdout ("stdio
 * transport"). A tool is four things: a name, a description Claude reads to decide when to
 * use it, an input schema, and a handler. The handler calls the REST API and returns text.
 * Claude does all the language understanding; this server only has to be clear about what
 * each tool is for.
 *
 * **Where this runs.** On the laptop, not the VPS. It calls the *deployed* API over HTTPS
 * with a bearer token, so all the validation and business rules stay in one place
 * (`CLAUDE.md §4`).
 *
 * **The one rule that will bite if forgotten:** on a stdio transport, stdout carries
 * protocol frames. A stray `console.log` writes into the middle of that stream and corrupts
 * it — the symptom is a server that "won't connect" with nothing useful in the logs. Every
 * diagnostic in this project goes to **stderr**, which Claude Desktop captures in its log
 * file and which is safe to write to at any time.
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';

import { ApiClient } from './apiClient.js';
import { loadConfig } from './config.js';
import { registerGetApplicationStats } from './tools/getApplicationStats.js';
import { registerGetUpcomingInterviews } from './tools/getUpcomingInterviews.js';
import { registerListPendingFollowups } from './tools/listPendingFollowups.js';
import { registerSearchApplications } from './tools/searchApplications.js';

/** stderr, always. See the note above about stdout. */
function log(message: string): void {
  process.stderr.write(`[jobtracker-mcp] ${message}\n`);
}

async function main(): Promise<void> {
  // Throws on a missing or malformed variable, which is the intended behaviour: a server
  // that starts without credentials would answer every question with a 401, and Claude
  // would report that as "you have no applications" rather than as a broken setup.
  const config = loadConfig();

  const server = new McpServer({
    name: 'job-tracker',
    version: '0.1.0',
  });

  const api = new ApiClient(config);

  // Four narrow tools rather than one "run any query" tool. Claude picks the right one from
  // the descriptions, and the set of data that can leave the API is fixed by what is
  // written here rather than by what someone asks for (PLAN.md Phase 6).
  registerGetApplicationStats(server, api);
  registerListPendingFollowups(server, api);
  registerSearchApplications(server, api);
  registerGetUpcomingInterviews(server, api);

  await server.connect(new StdioServerTransport());

  log(`ready — 4 read-only tools against ${config.baseUrl}`);
}

main().catch((cause: unknown) => {
  const message = cause instanceof Error ? cause.message : String(cause);
  log(`failed to start: ${message}`);
  process.exit(1);
});
