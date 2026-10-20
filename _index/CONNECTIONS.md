# Connections — generated, do not hand-edit

Rebuilt from every module card's frontmatter (`consumes` / `produces`) each time a card changes. If this file and a card disagree, the card is the source of truth — regenerate this file, don't patch it directly.

| Module | Produces | Consumed by |
|---|---|---|
| `core-data` | `data-repositories` | `roster`, `app-shell`, `attendance`, `student-feedback`, `homework`, `parents`, `export-backup` |
| `roster` | `roster-screens`, `class-student-picker`, `photo-storage` | `app-shell` (screens), `homework` + `parents` + `export-backup` (picker), `export-backup` (photo-storage) |
| `app-shell` | `share-helpers`, `app-theme` | `attendance`, `homework`, `parents`, `export-backup` |
| `attendance` | `attendance-screen` | `app-shell` |
| `student-feedback` | `feedback-screen`, `quick-note-screen`, `feedback-timeline` | `app-shell` (screens), `roster` (timeline) |
| `homework` | `homework-screen`, `attachment-storage` | `app-shell` (screen), `export-backup` (attachment-storage) |
| `parents` | `parents-screen` | `app-shell` |
| `export-backup` | `export-screen` | `app-shell` |
| `server-comms` | — (TBD at v3) | — (TBD at v3) |

**Cross-module seams so far:** `roster` → `student-feedback` (timeline embed); `homework` + `parents` + `export-backup` → `roster` (class/student picker: `ClassPicker` / `class-student-picker`) and → `app-shell` (file-capable `share-helpers` — **agreed and in use**: `shareFiles(context, uris, mimeTypes, text, subject)` for content URIs, `shareText(context, text, subject)` for text-only, `File.asShareableUri(context)` for the FileProvider URI); `export-backup` → `core-data` (checkpoint/DB-path hook for backup — shape to be agreed) and → `homework` (`attachment-storage`: `<filesDir>/attachments/`, the `attachments/` FileProvider entry) + `roster` (`photo-storage`: `<filesDir>/student_photos/`); `app-shell` → feature screens and the single shared `FileProvider` (composition root, `res/xml/file_paths.xml`). All data flows run through `data-repositories`; files move only via SAF `content://` Uris or the share sheet.

Edges only — what connects to what, and in what shape. Never the modules' internals; that stays inside each card.
