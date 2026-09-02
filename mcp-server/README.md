# mcp-server — Job Application Tracker

A read-only [MCP](https://modelcontextprotocol.io) server that lets Claude Desktop answer
questions about the job search in `../backend`. It runs **locally** and calls the
**deployed** API at `https://app4jobtrack.me` over HTTPS with a bearer token, so all the
validation and business rules stay in one place (`CLAUDE.md §4`).

## What MCP is, briefly

The Model Context Protocol lets an LLM client discover and call *tools* you define. Claude
Desktop launches this server as a child process and talks to it over the process's stdin and
stdout ("stdio transport"). A tool is four things: a name, a description Claude reads to
decide when to use it, an input schema, and a handler. Claude does all the language
understanding — this server only has to be clear about what each tool is for and return
readable text.

## The four tools

Each maps to one endpoint. Four narrow tools rather than one "run any query" tool: Claude
picks the right one from the descriptions, and what can leave the API is fixed by what is
written here rather than by what somebody asks for.

| Tool | Endpoint | Answers |
|---|---|---|
| `get_application_stats` | `GET /api/stats` | "How many applications this month?", response/offer/ghost rates, the funnel |
| `list_pending_followups` | `GET /api/applications/followups` | "What should I chase?", "which companies haven't I heard back from in 2+ weeks?" |
| `search_applications` | `GET /api/applications?q=` | "Show me everything related to Stripe" |
| `get_upcoming_interviews` | `GET /api/applications/interviews?days=` | "What interviews do I have this week?" |

## Run it locally

```bash
npm install
cp .env.example .env      # then fill in API_TOKEN
npm run build             # tsc -> dist/
npm run dev               # runs from src/ via tsx, reading .env
npm run inspect           # the MCP Inspector, for poking at tools by hand
```

`API_TOKEN` must equal `APP_MCP_TOKEN` on the server:

```bash
ssh app4jobtracker 'sudo grep APP_MCP_TOKEN /etc/jobtracker/jobtracker.env'
```

## Wire it into Claude Desktop

`~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "job-tracker": {
      "command": "/Users/duynguyen/.nvm/versions/node/v25.2.1/bin/node",
      "args": ["/Users/duynguyen/Developer/Spring-Boot-Projects/Application-Tracker/mcp-server/dist/index.js"],
      "env": {
        "API_BASE_URL": "https://app4jobtrack.me",
        "API_TOKEN": "<the APP_MCP_TOKEN value>"
      }
    }
  }
}
```

Restart Claude Desktop after editing. Then ask it "what interviews do I have this week?".

### Two things about that config that will cost you an afternoon

**`command` must be an absolute path to `node`.** Claude Desktop launches the server with a
minimal environment, not your shell's — so a bare `"node"` is not on `PATH` and the server
fails to start with no useful message. Verified here by launching the exact configured
command with `PATH=/usr/bin:/bin` from `/`.

**Node is installed via nvm, so that absolute path contains a version number** and will
break the next time you `nvm install` a newer Node. When Claude Desktop suddenly reports the
server failing to start, this is why. Repoint it:

```bash
python3 - <<'PY'
import json, os, pathlib, shutil
p = pathlib.Path(os.path.expanduser('~/Library/Application Support/Claude/claude_desktop_config.json'))
cfg = json.loads(p.read_text())
cfg['mcpServers']['job-tracker']['command'] = shutil.which('node')
p.write_text(json.dumps(cfg, indent=2) + '\n')
print('now:', cfg['mcpServers']['job-tracker']['command'])
PY
```

## Layout

| Path | What |
|---|---|
| `src/index.ts` | Creates the server, registers the four tools, connects the stdio transport |
| `src/config.ts` | Reads and validates `API_BASE_URL` / `API_TOKEN`; fails closed at startup |
| `src/apiClient.ts` | The only code that touches the network. Exposes `get` and nothing else |
| `src/types.ts` | TypeScript mirrors of the backend response DTOs (a subset of the SPA's) |
| `src/format.ts` | API JSON to the text Claude reads — dates, timezones, enum labels |
| `src/tools/*.ts` | One file per tool: schema, description, handler |
| `src/tools/result.ts` | The `{content:[{type:"text"}]}` shape, and error-to-text mapping |

## Design notes

**Read-only, twice over.** `ApiClient` exposes only `get`, so a write cannot be *spelled* in
this codebase. Independently, the backend's bearer filter chain permits `GET /api/**` and
denies everything else — verified against the deployed instance: `POST`, `PUT`, `PATCH` and
`DELETE` all return 403 with this token, and nothing was created. The backend is the
guarantee; this client is the politeness.

**Never write to stdout.** On a stdio transport stdout carries protocol frames, and a stray
`console.log` corrupts the stream — the symptom is a server that "won't connect" with
nothing useful in the logs. Every diagnostic goes to stderr, which Claude Desktop captures.

**Calendar vs rolling windows.** "How many applications this month?" means a *calendar*
month, and `days=30` cannot express it. The API takes `days` **or** `from`/`to` and echoes
back which window it measured; `get_application_stats` tells Claude which to reach for and
prints the window above the numbers, so a rolling count is never reported as "this month"
(`SCHEMA.md §10.1`).

**Times carry their timezone.** `scheduledAt` is a UTC instant. Rendering "14:00" with no
zone invites Claude to repeat it as a local time. Every interview time is formatted in the
display timezone with its abbreviation attached (`Fri, Sep 4, 2026, 14:00 PDT`), and the
list states the zone once in its header. `APP_TIMEZONE` overrides the display zone; it
defaults to the machine's own.

**Partial-word search is advertised.** The backend uses an escaped case-insensitive regex
rather than a `$text` index precisely so `strip` finds Stripe (`SCHEMA.md §6`). The tool
description says so, or Claude "helpfully" corrects the user's term first. The query is
passed through unescaped on purpose — the backend applies `Pattern.quote`, and escaping at
both ends would double it.
