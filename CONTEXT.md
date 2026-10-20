# Teacher Classroom App (Android) — the module map

A local, on-device prototype that lets one teacher take attendance, log feedback, and manage homework faster than their Excel sheet — shared out manually, no server.

| Module | Does | Status | Build | Consumes | Produces |
|---|---|---|---|---|---|
| `core-data` | Room + repositories, one data layer | drafted | built | — | `data-repositories` |
| `roster` | classes, students, student page, CSV/Excel import | drafted | built | `data-repositories` | `class-student-picker`, `photo-storage` |
| `app-shell` | nav, theme, Today home, share helpers | drafted | built | `data-repositories`, `*-screens` (7 edges) | `share-helpers`, `app-theme` |
| `attendance` | tap-absentees screen, counters, history | drafted | built | `data-repositories`, `share-helpers` | `attendance-screen` |
| `student-feedback` | quick notes with preset tags | drafted | built | `data-repositories` | `feedback-screen`, `quick-note-screen`, `feedback-timeline` |
| `homework` | compose + attachments + share | drafted | built | `data-repositories`, `share-helpers`, `class-student-picker` | `homework-screen`, `attachment-storage` |
| `parents` | announcements + parent contact log | drafted | not-started | `data-repositories`, `class-student-picker`, `share-helpers` | `parents-screen` |
| `export-backup` | summaries, CSV/PDF export, backup | drafted | not-started | `data-repositories`, `class-student-picker`, `share-helpers`, `photo-storage`, `attachment-storage` | `export-screen` |
| `server-comms` | server transport/API/auth/sync — **v3 placeholder, not researched** | planned | not-started | — (TBD at v3) | — (TBD at v3) |

`Status` is the blueprint's own state (`planned` · `drafted` · `needs-redo`). `Build` is the implementing agents' state (`not-started` · `in-progress` · `blocked` · `built` · `needs-rework`). They move independently: a card can be fully drafted long before any code exists. Live build state and the exact commits live in `_index/MODULES.md` and each card's `Build progress`.

Global constraints every module inherits: `_shared/project-constraints.md`.
Shared vocabulary and cross-module decisions (created during grilling): `_shared/glossary.md`, `_shared/adr/`.
Full status and connection graph: `_index/MODULES.md`, `_index/CONNECTIONS.md`.
