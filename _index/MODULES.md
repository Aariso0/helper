# Modules — status checklist

Read this first when resuming. One line per module. Update the status the moment work on a module starts or stops — this file is the entire memory of where things stand.

- [x] `core-data` — one local data layer: Room, repositories, IDs/timestamps — status: drafted — build: built
- [x] `roster` — classes, students, student page, Excel/CSV import — status: drafted — build: not-started
- [x] `app-shell` — nav, theme, Today home screen, share helpers — status: drafted — build: not-started
- [x] `attendance` — tap-absentees screen, counters, edit past dates — status: drafted — build: not-started
- [x] `student-feedback` — quick notes with editable preset tags — status: drafted — build: not-started
- [x] `homework` — compose with attachments, share via share sheet — status: drafted — build: not-started
- [x] `parents` — manual announcements + parent contact log — status: drafted — build: not-started
- [x] `export-backup` — summaries, CSV/PDF export, manual backup/restore — status: drafted — build: not-started
- [ ] `server-comms` — how the app talks to a server (transport, API, auth, sync) — status: planned — build: not-started — **v3 placeholder: do not research or build before v3** (card: `modules/server-comms.md`)

Draft order (foundational first): `core-data` → `roster` → `app-shell` → `attendance` → `student-feedback` → `homework` → `parents` → `export-backup`. `server-comms` is outside every v0–v2 pass; it enters the order only when the user opens the v3 server phase.

Statuses: `planned` (card stubbed, not researched) · `researching` (in progress) · `drafted` (research settled, card complete) · `needs-redo` (user changed # In Use / # Excluded, update pending).

Build states (`build:`): `not-started` · `in-progress` · `blocked` · `built` · `needs-rework`. Mirrors each card's frontmatter; the card wins on any disagreement. Set by the build team's lead, not the blueprint skill.

Check the box only at `drafted` (the box tracks the blueprint, never the build). A `needs-redo` module gets unchecked until it's `drafted` again.
