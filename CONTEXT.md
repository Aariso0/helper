# Teacher Classroom App (Android) — the module map

A local, on-device prototype that lets one teacher take attendance, log feedback, and manage homework faster than their Excel sheet — shared out manually, no server.

| Module | Does | Status | Consumes | Produces |
|---|---|---|---|---|
| `core-data` | Room + repositories, one data layer | drafted | — | `data-repositories` |
| `roster` | classes, students, student page, CSV/Excel import | drafted | `data-repositories` | `class-student-picker`, `photo-storage` |
| `app-shell` | nav, theme, Today home, share helpers | drafted | `data-repositories`, `*-screens` (7 edges) | `share-helpers`, `app-theme` |
| `attendance` | tap-absentees screen, counters, history | drafted | `data-repositories`, `share-helpers` | `attendance-screen` |
| `student-feedback` | quick notes with preset tags | drafted | `data-repositories` | `feedback-screen`, `quick-note-screen`, `feedback-timeline` |
| `homework` | compose + attachments + share | drafted | `data-repositories`, `share-helpers`, `class-student-picker` | `homework-screen`, `attachment-storage` |
| `parents` | announcements + parent contact log | drafted | `data-repositories`, `class-student-picker`, `share-helpers` | `parents-screen` |
| `export-backup` | summaries, CSV/PDF export, backup | drafted | `data-repositories`, `class-student-picker`, `share-helpers`, `photo-storage`, `attachment-storage` | `export-screen` |
| `server-comms` | server transport/API/auth/sync — **v3 placeholder, not researched** | planned | — (TBD at v3) | — (TBD at v3) |

"Done" here means the module's card is fully researched and drafted — its stack decided, its connections defined. It does not mean code exists; this workspace never contains code.

Global constraints every module inherits: `_shared/project-constraints.md`.
Shared vocabulary and cross-module decisions (created during grilling): `_shared/glossary.md`, `_shared/adr/`.
Full status and connection graph: `_index/MODULES.md`, `_index/CONNECTIONS.md`.
