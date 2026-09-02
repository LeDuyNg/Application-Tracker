/**
 * The one place a stored URL becomes a clickable link.
 *
 * Why this exists: `jobPostingUrl` and `company.website` are free text the user typed, and
 * React does *not* sanitise the `href` attribute — it escapes text content, but
 * `<a href={value}>` with `value = "javascript:alert(document.cookie)"` produces a link
 * that runs script in this app's own origin when clicked. That is stored XSS. The backend
 * now rejects non-http(s) URLs at write time (`Validation.HTTP_URL`), but that cannot help
 * with rows written before the constraint existed, so the render site refuses them too.
 *
 * `new URL()` is the parser rather than a regex on purpose: it resolves the tricks a regex
 * misses — leading whitespace and control characters, `JaVaScRiPt:`, `java\tscript:` — by
 * normalising the value before we ever look at its protocol.
 */
export function safeUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  try {
    const parsed = new URL(value);
    // An allowlist, not a blocklist: `javascript:`, `data:`, `vbscript:`, `blob:` and
    // anything invented later are all refused by not being on it.
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : null;
  } catch {
    // Not an absolute URL at all (a bare "example.com", or nonsense). Not linkable.
    return null;
  }
}
