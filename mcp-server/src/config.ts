/**
 * Configuration, read once at startup from the environment.
 *
 * Two variables, and both are required. Where they come from depends on how the server is
 * launched (CLAUDE.md §8):
 *
 * - **Claude Desktop** passes them in the `env` block of `claude_desktop_config.json`.
 *   That is the real path, and it does not read a `.env` file.
 * - **Local dev** uses `npm run dev`, which passes `node --env-file=.env`. The `.env` is
 *   gitignored and exists only on this machine.
 *
 * **Fail closed, loudly, at startup.** The same reasoning the backend applies to
 * `APP_ALLOWED_EMAILS` and `app.mcp-token` (CLAUDE.md §6): a missing credential must stop
 * the process, not produce a server whose every tool call fails with a 401 that Claude then
 * reports to the user as "you have no applications". A wrong answer stated confidently is
 * worse than an obvious crash — and in a stdio server, a crash at startup is exactly what
 * Claude Desktop surfaces as "server failed to start", which points at the right place.
 */

/** Read one required variable, or explain precisely what is missing and where it goes. */
function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(
      `${name} is not set.\n` +
        `  • Claude Desktop: add it to the "env" block for this server in ` +
        `claude_desktop_config.json\n` +
        `  • Local dev: add it to mcp-server/.env (see .env.example)`,
    );
  }
  return value;
}

/**
 * `API_BASE_URL` with any trailing slash removed, so joining a path is always
 * `${baseUrl}${path}` with exactly one slash between them.
 */
function readBaseUrl(): string {
  const raw = required('API_BASE_URL');

  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    throw new Error(`API_BASE_URL is not a valid URL: ${raw}`);
  }

  // The bearer token is sent on every request. Over plain http it travels in cleartext, and
  // this token reads the entire job search. `localhost` is exempt because that is the
  // backend running on this machine with no network hop to intercept.
  const isLocalhost = parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1';
  if (parsed.protocol !== 'https:' && !isLocalhost) {
    throw new Error(
      `API_BASE_URL must use https (got "${parsed.protocol}//" for ${parsed.hostname}). ` +
        `The bearer token is sent on every request and would otherwise be in cleartext.`,
    );
  }

  return raw.replace(/\/+$/, '');
}

export interface Config {
  readonly baseUrl: string;
  readonly token: string;
}

export function loadConfig(): Config {
  return {
    baseUrl: readBaseUrl(),
    token: required('API_TOKEN'),
  };
}
