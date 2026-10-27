# Modules — status checklist

Read this first when resuming. One line per module. Update the status the moment work on a module starts or stops — this file is the entire memory of where things stand.

- [x] `core-data` — one local data layer: Room, repositories, IDs/timestamps — status: drafted — build: built
- [x] `roster` — classes, students, student page, Excel/CSV import — status: drafted — build: built
- [x] `app-shell` — nav, theme, Today home screen, share helpers — status: drafted — build: needs-rework (motion pass superseded by `animations`, 2026-10-26)
- [x] `animations` — spring motion system: tokens, nav transitions, shared elements, micro-interactions — status: drafted — build: not-started (card: `modules/animations/animations.md`)
- [x] `attendance` — tap-absentees screen, counters, edit past dates — status: drafted — build: built
- [x] `student-feedback` — quick notes with editable preset tags — status: drafted — build: built
- [x] `homework` — compose with attachments, share via share sheet — status: drafted — build: built
- [x] `parents` — manual announcements + parent contact log — status: drafted — build: built
- [x] `export-backup` — summaries, CSV/PDF export, manual backup/restore — status: drafted — build: built
- [x] `exams-results` — exam creation, fast mark entry, totals/percentages/pass-fail, analysis, Excel export with formulas and colors — status: drafted — build: built (card: `modules/exams-results.md`)
- [ ] `server-comms` — how the app talks to a server (transport, API, auth, sync) — status: planned — build: not-started — **v3 placeholder: do not research or build before v3** (card: `modules/server-comms.md`)

Draft order (foundational first): `core-data` → `student-feedback` → `roster` → `app-shell` → `attendance` → `homework` → `parents` → `export-backup`. `animations` is a cross-cutting module added 2026-10-26; it lands after `app-shell` and touches every built screen, so it is built as a follow-up pass rather than as part of the original order. `server-comms` is outside every v0–v2 pass; it enters the order only when the user opens the v3 server phase.

> 2026-10-26 — `animations` drafted. It replaces `app-shell`'s tween-based `theme/Motion.kt` with springs, so `app-shell` moves to `build: needs-rework` until the motion module lands there. No dependency, BOM or Kotlin version changes.

Statuses: `planned` (card stubbed, not researched) · `researching` (in progress) · `drafted` (research settled, card complete) · `needs-redo` (user changed # In Use / # Excluded, update pending).

Build states (`build:`): `not-started` · `in-progress` · `blocked` · `built` · `needs-rework`. Mirrors each card's frontmatter; the card wins on any disagreement. Set by the build team's lead, not the blueprint skill.

Check the box only at `drafted` (the box tracks the blueprint, never the build). A `needs-redo` module gets unchecked until it's `drafted` again.
