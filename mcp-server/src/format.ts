/**
 * Turning API JSON into the text Claude reads.
 *
 * Every tool returns text, not JSON. That is deliberate: Claude reads the tool result as
 * context, so a compact labelled summary costs far fewer tokens than a pretty-printed
 * object and is much harder to misread than a wall of `"currentStageType":"SUPERDAY"`.
 *
 * The rules that matter for correctness rather than taste:
 *
 * - **Times are rendered in the owner's timezone and always carry the zone abbreviation.**
 *   `scheduledAt` is a UTC instant; showing "16:00" with no zone invites Claude to repeat it
 *   as a local time, which is off by hours. `SCHEMA.md §7` is the standing warning about
 *   exactly this family of bug.
 * - **Nothing is invented.** Where the API sends `null` the text says so or omits the line;
 *   it never substitutes a default that would read as fact.
 */

/**
 * The zone times are displayed in.
 *
 * Defaults to the machine's own timezone, which on the laptop this server runs on is the
 * owner's — the zero-config right answer. `APP_TIMEZONE` overrides it, which matters if the
 * laptop travels or if the display zone should be pinned to the backend's `app.timezone`
 * (`America/New_York`) regardless of where you are sitting.
 *
 * Resolved once at module load. An invalid value would otherwise throw on every single
 * format call, deep inside a tool, as something unrelated-looking.
 */
const TIME_ZONE: string = resolveTimeZone();

function resolveTimeZone(): string {
  const configured = process.env['APP_TIMEZONE']?.trim();
  if (!configured) {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  }
  try {
    // Throws RangeError on an unknown zone. Better here, once, than per call.
    new Intl.DateTimeFormat('en-US', { timeZone: configured }).format(new Date());
    return configured;
  } catch {
    process.stderr.write(
      `[jobtracker-mcp] APP_TIMEZONE="${configured}" is not a valid IANA zone; ` +
        `falling back to the system timezone.\n`,
    );
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  }
}

/** The display timezone, exposed so a tool can state it once in its output. */
export const displayTimeZone = TIME_ZONE;

/**
 * `TECHNICAL_INTERVIEW` -> `Technical interview`.
 *
 * The backend serialises enums by their Java constant name (`SCHEMA.md §5`), which is
 * precise but shouty. Claude reads either fine; the difference shows up when it quotes a
 * value back to the user in a sentence.
 */
export function humanise(constant: string): string {
  const words = constant.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * An ISO-8601 instant as e.g. `Fri, Sep 4, 2026, 14:00 PDT`.
 *
 * The locale is `en-US` for one specific reason: it renders `timeZoneName: 'short'` as a
 * real abbreviation (`PDT`, `EDT`), where `en-GB` renders the same field as `GMT-7`. The
 * zone is the part that stops Claude repeating a UTC clock time as a local one, so it
 * should read as a zone rather than as an offset.
 */
export function formatInstant(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso; // Unparseable: show what arrived rather than "Invalid Date".
  }
  return new Intl.DateTimeFormat('en-US', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: TIME_ZONE,
    timeZoneName: 'short',
  }).format(date);
}

/**
 * An ISO date (`2026-08-15`) as `15 Aug 2026`.
 *
 * Parsed as a plain calendar date, **not** through `new Date("2026-08-15")` — that parses
 * as midnight *UTC*, so anywhere west of Greenwich it renders as the previous day. The
 * date-only fields (`appliedDate`, `followUpDate`) have no time component by design
 * (`SCHEMA.md §7`) and must not acquire one on the way to being displayed.
 */
export function formatDate(iso: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!match) {
    return iso;
  }
  const [, year, month, day] = match;
  const asUtcNoon = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day), 12));
  return new Intl.DateTimeFormat('en-US', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC', // Read back in the same zone it was built in.
  }).format(asUtcNoon);
}

/**
 * An ISO-8601 instant rendered as a bare date in the display timezone — `25 Aug 2026`.
 *
 * For fields like `lastContactAt`, where the clock time is noise but the day matters.
 * Note this is **not** `formatDate` on the leading `YYYY-MM-DD` of the string: that is the
 * date in *UTC*, so an instant at `2026-08-25T02:00Z` would print as 25 August while it was
 * still the 24th where the owner is.
 */
export function formatInstantAsDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return new Intl.DateTimeFormat('en-US', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: TIME_ZONE,
  }).format(date);
}

/** `3` -> `3 days`, `1` -> `1 day`. Small thing; it shows up in every list. */
export function pluralDays(count: number): string {
  return `${count} ${Math.abs(count) === 1 ? 'day' : 'days'}`;
}

/** A percentage the backend already computed, to one decimal place. */
export function formatPct(value: number): string {
  return `${value.toFixed(1)}%`;
}

/** Join non-empty parts with ` · `, dropping anything null/blank. */
export function joinParts(...parts: Array<string | null | undefined>): string {
  return parts.filter((part): part is string => Boolean(part && part.trim())).join(' · ');
}
