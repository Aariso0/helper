---
module: homework
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["data-repositories", "share-helpers", "class-student-picker"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["homework-screen", "attachment-storage"]      # what this module exposes to others (use _shared/glossary.md terms)
build: not-started   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-20
---

# Homework

Compose homework per class — title, text, image/PDF/PPT attachment, camera shortcut, due date — and share manually via the Android share sheet. PDF-first: PPT attaches and shares as-is but is never previewed.

## # In Use
- **SAF pickers (`androidx.activity`)** — same picker stack as roster for multi-file attachments; no storage permissions.
- **`TakePicture` + app `FileProvider`** — the official capture path: one-tap whiteboard photo straight into attachments, no custom camera UI.
- **`share-helpers` (ACTION_SEND / ACTION_SEND_MULTIPLE)** — sharing is the system share sheet with `content://` URIs; WhatsApp etc. receive files without any vendor SDK.
- **Coil** — attachment image thumbnails (same dependency roster introduced).
- **Internal `attachment-storage`** — files copied into app-internal storage; paths recorded on the `Attachment` entity.

## # Excluded
- **In-app PDF renderer (PdfiumAndroid / android-pdfviewer / PSPDFKit)** — an APK-growing rendering dependency for a view the send flow doesn't need; PDF opens via a system intent instead. Lost a close call.
- **CameraX / custom camera screen** — a full camera module when the system camera intent (`TakePicture`) delivers the one-shot whiteboard capture in one tap.
- **WhatsApp/cloud upload, any messaging SDK** — user-specified: sharing is manual via the share sheet; also no internet permission allowed.
- **MediaStore / shared storage for attachments** — children's-data privacy plus the no-permissions posture: attachments live app-internally only.
- **Saving attachments to server/cloud (README "Skip for now")** — user-specified project constraint; files stay on device.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| Attachment picking | SAF `OpenMultipleDocuments` + `PickVisualMedia` | `androidx.activity 1.13.0` | same picker stack as roster; zero storage permissions |
| Camera shortcut | `TakePicture` contract + `FileProvider` | `androidx.core` (project-managed) | official one-tap capture; no camera library |
| Image thumbnails | `coil-compose` | `3.6.3` | already a project dependency (roster); Compose-first |
| State | ViewModel + StateFlow | `2.11.0` (lifecycle) | draft form + attachment list as unidirectional state |
| Persistence | `data-repositories` | `core-data` contract | Homework/Attachment rows; draft writes are immediate |
| Due date | `java.time.LocalDate` (desugared) | `desugar_jdk_libs 2.1.5` | same date stack as attendance/Today |
| Sharing | `share-helpers` (file-capable) | `app-shell` contract | system share sheet, ACTION_SEND_MULTIPLE with content URIs |

## File & folder layout
```
homework/
├─ viewmodel/            HomeworkViewModel: draft (class, title, text, due), attachments, commit
├─ ui/
│  ├─ HomeworkList.kt      Homework tab: assignments per class + "New homework"
│  ├─ HomeworkEdit.kt      class picker → title → text → attachments → due → Share
│  ├─ AttachmentRow.kt     thumbnail/icon, remove; PDF opens via system intent, PPT has no open affordance
│  └─ CameraShortcut.kt    whiteboard capture (TakePicture) → attachment
└─ attachmentstore/      copy picked/captured bytes into app-internal attachments dir; path bookkeeping
```
Relative to the app module's main source set, under the project's root namespace.

## Setup sequence
1. Confirm `core-data` (`data-repositories`), `app-shell` (`share-helpers`, `app-theme`), and `roster` (`class-student-picker`) interfaces are available; the dependencies (coil `3.6.3`, androidx.activity `1.13.0`) are already in the project from `roster`.
2. Confirm the `FileProvider` declared by `app-shell` (manifest authority + `file_paths`) covers the attachments directory — coordinate with the `app-shell` owner rather than declaring a second provider.
3. Create `attachmentstore/` — copy-on-pick into app-internal storage for picked files and camera captures alike; record paths on the `Attachment` entity through repositories.
4. Create `viewmodel/` and `ui/` — list, edit flow, attachment rows, camera shortcut.
5. Hand `homework-screen` to `app-shell` for route wiring; document `attachment-storage` for `export-backup`.

## Why this shape
The prototype's homework feature is judged by "share in under a minute including an attachment", so the flow is linear and every heavy capability was replaced by a platform one: picking through SAF (no permissions), capture through the system camera via `TakePicture` (no CameraX module to build and maintain), and delivery through the share sheet (no WhatsApp integration, no cloud upload, no internet permission). Files are copied into app-internal storage the moment they're picked — matching roster's `photo-storage` posture — because attachments hold student-facing material (whiteboard photos) that must not sit in shared storage, and because a local file with a recorded path is exactly what the later backup phase and eventual server phase will move. PDF-first, per the user's decision: the UI nudges toward PDF, PDFs open through a system viewer intent when one exists (graceful snackbar when none does), while PPT files attach and share perfectly but carry no open affordance — sharing was never the question, previewing was, and an in-app renderer (Pdfium et al.) is a real dependency for a view the send flow doesn't require. The camera shortcut exists because the README's signature use case is photographing a whiteboard in one step; it feeds the same attachment store, so the rest of the flow can't tell the difference. Draft state commits as it's typed (rule 5) while attachment bytes are copied immediately — the only lifecycle the module owns is deleting unreferenced attachment files when a draft is abandoned or its files replaced.

## Connections
- **Consumes:** `data-repositories` from `core-data` — Homework and Attachment rows, Class names for lists; `class-student-picker` from `roster` — choosing the target class in the compose flow; `share-helpers` from `app-shell` — the file-capable share action (text + content URIs + mime types; agree the exact shape with `app-shell`'s owner before coding against it, per the build protocol).
- **Produces:** `homework-screen` — Homework tab root composable (typed params + `onOpenClass` callback); `attachment-storage` — the app-internal attachments directory convention, documented for `export-backup` alongside roster's `photo-storage`.
- **Talks to via:** in-process composables; routes wired by `app-shell`, class choice delegated to `roster`'s picker component.

## Open questions
- None. (Whether homework status per student is *tracked* — README 3.2 UNKNOWN — stays untracked: the prototype assigns and shares, it does not grade, and nothing on any card depends on it.)

## Build notes for the implementing agent
- Agree the `share-helpers` file-sharing signature with the `app-shell` owner before writing share code — that's a cross-module seam (log it in the group chat).
- PPT rows show icon + filename only: no tap-to-open, no preview. PDF rows open via an `ACTION_VIEW` intent with the FileProvider `content://` URI and a friendly snackbar when no viewer app exists.
- Respect EXIF orientation on camera captures (whiteboard photos are frequently rotated); thumbnails via Coil must render correctly.
- Clean up orphaned attachment files (copied but never referenced by a saved Homework) when a draft is abandoned or replaced — attachments hold student-facing material; do not accumulate junk.
- Keep accepting whatever the SAF returns (legacy `.ppt`, older encodings): tolerate and share, never reject — but only ever *nudge* PDF, never block PPT (user decision).
- No INTERNET permission, no MediaStore writes, no upload paths of any kind.

## Build progress
{Left blank by the blueprint skill. Implementing agents fill and update this after every chunk of work.}
- **Owner:** {agent / teammate name, or none}
- **Coded:**
  - {`path/to/file` — what is done in it}
- **Remaining** (next first):
  1. {next piece of work}
- **Blocked on:** {nothing / what and who}
- **Handoff note:** {what the next agent should know — where you stopped, any trap}
- **Last checkpoint:** {ISO timestamp}
