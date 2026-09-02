/**
 * Date + number formatting. `date-fns` is the only date library in the project (CLAUDE.md
 * §3) — no moment/dayjs/luxon.
 *
 * A note on timezones: the backend does all "this week / due soon" boundary math in the
 * owner's zone and hands us plain instants and ISO dates. Here we only *display*, so we
 * format in the browser's local zone and don't do any window arithmetic of our own.
 */
import { format, formatDistanceToNowStrict, isValid, parseISO } from 'date-fns';

/** "2026-08-15" (a LocalDate) → "Aug 15, 2026". */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = parseISO(iso);
  return isValid(d) ? format(d, 'MMM d, yyyy') : '—';
}

/** An Instant → "Aug 15, 2026, 3:00 PM" in local time. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = parseISO(iso);
  return isValid(d) ? format(d, 'MMM d, yyyy, h:mm a') : '—';
}

/** An Instant → "3 days ago" / "in 2 hours". */
export function formatRelative(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = parseISO(iso);
  return isValid(d) ? formatDistanceToNowStrict(d, { addSuffix: true }) : '—';
}

/** `<input type="date">` wants "yyyy-MM-dd"; a LocalDate already is that, so pass through. */
export function toDateInputValue(iso: string | null | undefined): string {
  return iso ? iso.slice(0, 10) : '';
}

/** An Instant → "yyyy-MM-ddTHH:mm" for `<input type="datetime-local">`, in local time. */
export function toDateTimeInputValue(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = parseISO(iso);
  if (!isValid(d)) return '';
  // Shift by the local offset so the wall-clock time shown matches the stored instant.
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

/** "yyyy-MM-ddTHH:mm" from a datetime-local input → a real ISO instant string (UTC). */
export function fromDateTimeInputValue(value: string): string | null {
  if (!value) return null;
  const d = new Date(value); // interpreted as local time by the JS engine
  return isValid(d) ? d.toISOString() : null;
}

export function formatCompensation(
  comp: { min: number | null; max: number | null; currency: string | null } | null | undefined,
): string {
  if (!comp || (comp.min == null && comp.max == null)) return '—';
  const ccy = comp.currency ?? 'USD';
  const money = (n: number) => `${ccy} ${n.toLocaleString()}`;
  if (comp.min != null && comp.max != null) return `${money(comp.min)}–${comp.max.toLocaleString()}`;
  return money((comp.min ?? comp.max) as number);
}

export function formatPercent(value: number | null | undefined): string {
  return value == null ? '—' : `${value.toFixed(0)}%`;
}
