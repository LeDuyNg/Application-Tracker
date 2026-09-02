# Capture checklist — the README's images and transcripts

`README.md` has five slots waiting on assets only you can produce. Each is marked with an
HTML comment at the point it belongs. Save files to `docs/images/` with the exact names
below and nothing else needs editing.

> **Before you screenshot anything: this is your real job search.** Company names, recruiter
> contacts, notes and compensation figures are all on screen, and this repo is public. Decide
> per shot whether to show real data, blur a column, or seed a couple of throwaway rows
> locally and shoot that instead. The dashboard and funnel read fine with invented companies;
> only you can judge the rest.

---

## 1. `docs/images/dashboard.png` — the hero shot

Top of the README, directly under the badges. The single most important image: it is what a
recruiter sees before deciding whether to keep scrolling.

- Sign in at <https://app4jobtrack.me> and land on the Dashboard.
- Browser window ~1400px wide, sidebar **expanded**. Zoom to 100%.
- Capture the viewport, not the whole page — stats bar, follow-ups and upcoming-interviews
  widgets visible together.
- macOS: `⌘⇧4` then `Space` and click the window, which gives a clean shot with a shadow.
- Crop out the browser chrome, tabs and URL bar.

## 2. `docs/images/datadog-dashboard.png` — the Datadog dashboard

In the Observability section.

- Datadog → Dashboards → **Job Tracker — API**.
- Set the time range to the widest window with real data (7d or 30d). A dashboard of flat
  zero lines is worse than no screenshot.
- Include the dashboard **title bar** so it is visibly Datadog and visibly yours.
- Do **not** capture the org/account switcher or an API key if one is on screen.

---

## 3–5. MCP transcripts

Three slots in the MCP section, currently HTML comments. Run each query in Claude Desktop,
expand the tool-call block so both the arguments and the result are visible, and either
screenshot it or paste the text.

| Query | Should call |
|---|---|
| "How many applications have I sent this month?" | `get_application_stats` with `from`/`to` |
| "Which companies haven't I heard back from in 2+ weeks?" | `list_pending_followups` |
| "What interviews do I have this week?" | `get_upcoming_interviews` |

**Text beats a screenshot here**, and it is the format the README slots assume — it stays
readable on a phone, is searchable, and survives GitHub's image rendering. Something like:

````markdown
> **"How many applications have I sent this month?"**
>
> ```
> get_application_stats({ from: "2026-09-01", to: "2026-09-30" })
> ```
>
> You've sent 3 applications in September so far (Sep 1–30)…
````

If you screenshot instead, save as `docs/images/mcp-<n>.png` and replace the comment with an
image tag.

**Worth trying deliberately:** ask *"how many this month vs the last 30 days?"* — it should
produce two calls with different windows and name each one. That is the calendar-vs-rolling
distinction working, and it is a better transcript than any single query, because it shows
the model choosing between two windows rather than just fetching a number.

---

## When the assets are in

```bash
grep -n "SCREENSHOT\|TRANSCRIPT" README.md    # every remaining slot
```

Delete each comment as you fill it. When that returns nothing, the README is complete.
