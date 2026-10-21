---
module: parents
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["data-repositories", "class-student-picker", "share-helpers"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["parents-screen"]      # what this module exposes to others (use _shared/glossary.md terms)
build: built   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-21
---

# Parents

Announcements the teacher composes (with templates for common ones) and shares manually to parent groups, plus a log of parent feedback received by call or message. Composing and sharing are both manual — no parent-facing screens exist.

## # In Use
- **Framework `PdfDocument`** — zero-dependency text→PDF for parents who want a document instead of a message.
- **`share-helpers` (file-capable)** — the share sheet delivers the announcement text or PDF; the clipboard handles "copy as text".
- **`class-student-picker`** — choose the target Class for an announcement and the Student for a contact-log entry; no second picker UI.
- **`AnnouncementTemplate` entity (via `data-repositories`)** — real template rows so recurring messages are one tap, added to `core-data`'s schema before first build.
- **ViewModel + StateFlow** — drafts, template edits, and log entries as unidirectional state.

## # Excluded
- **iText** — AGPL/commercial dual license; an unusual restriction the prototype must not inherit.
- **Apache PDFBox / pdfbox-android, OpenPDF** — desktop-JVM document libraries; heavyweight Android packaging for rendering a few paragraphs.
- **androidx.pdf (pdf-viewer)** — still `1.0.0-alpha` and is a *viewer* with forms/OCR, not a simple text→PDF writer; alpha plus wrong shape.
- **Contacts/groups database or WhatsApp integration** — parent groups aren't a data problem here: the share sheet opens whatever group the teacher already has (README: shared *manually*; also no internet permission).
- **Parent-facing screens, logins, push** — user-specified project constraint ("Skip for now").
- **Auto absence alerts / any automatic sending** — nothing in this prototype ever sends by itself; every share is teacher-initiated.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| UI components | Material 3 via Compose BOM | `2026.09.00` | list, text fields, dialogs built in |
| State | ViewModel + StateFlow | `2.11.0` (lifecycle) | announcement draft, templates, log as derived state |
| Persistence | `data-repositories` | `core-data` contract | Announcement / AnnouncementTemplate / ContactLogEntry rows |
| PDF rendering | `android.graphics.pdf.PdfDocument` | platform (API 19+) | zero-dependency paragraphs→PDF; no third-party license surface |
| Sharing & copy | `share-helpers` + system clipboard | `app-shell` contract | share sheet (text/PDF) or copy-as-text; user-initiated |
| Class/student choice | `class-student-picker` | `roster` contract | same picker as everywhere; one UI |
| Date & time | `java.time.LocalDate` (desugared) | `desugar_jdk_libs 2.1.5` | announcement/log dates on any min SDK |

## File & folder layout
```
parents/
├─ viewmodel/            ParentsViewModel: announcement draft, template edits, contact log entries
├─ ui/
│  ├─ ParentsScreen.kt     Parents tab root: announcements section + contact log section
│  ├─ AnnouncementEdit.kt  title/body, template picker, Share + Copy actions
│  ├─ TemplateChips.kt     recent templates first; save-as / rename / delete in place
│  └─ ContactLog.kt        per-student log list, add-entry flow with the roster picker
└─ pdfstore/             render announcement → app-internal PDF (PdfDocument), share via FileProvider
```
Relative to the app module's main source set, under the project's root namespace.

## Setup sequence
1. No new Gradle dependencies — confirm `data-repositories`, `class-student-picker`, the file-capable `share-helpers`, and `app-shell`'s `FileProvider` are in place, and that `core-data`'s schema includes `AnnouncementTemplate`.
2. Create `viewmodel/` — announcement draft, template list (recent first), contact-log entries, all through repositories.
3. Create `ui/` — `ParentsScreen` (two sections), `AnnouncementEdit` (Share → share sheet, Copy → clipboard), `TemplateChips` (in-place save/rename/delete), `ContactLog` (student chosen via `class-student-picker`).
4. Create `pdfstore/` — paragraph layout on standard pages via `PdfDocument`, written to the app-internal export/PDF directory already covered by `app-shell`'s `file_paths`.
5. Hand `parents-screen` to `app-shell` for route wiring.

## Why this shape
Every delivery path in this module is manual by design because parent access itself doesn't exist in the prototype: the share sheet sends to groups the teacher already has, the clipboard covers paste-into-anything, and the PDF option exists because the README names text/PDF explicitly — all three are user-initiated, zero-permission, zero-internet. `PdfDocument` won the rendering choice outright on licensing alone (iText is AGPL), then again on weight (PDFBox/OpenPDF are desktop document suites) and recency (androidx.pdf is an alpha viewer, wrong shape); it renders a titled page of paragraphs, which is all an announcement PDF is. Templates became a real entity rather than "duplicate a past announcement" because README 3.5 explicitly asks for templates for *common* ones, and recurring messages ("no school Monday") get buried under date-specific history when the only mechanism is duplication — adding the entity to `core-data`'s schema before the first build costs nothing, while adding it later costs a migration, which is exactly the kind of cheap-now/expensive-later habit the README's "optional low-cost" section describes. The contact log is per-student rows entered through the same roster picker every other module uses, kept recent-first (rule 7) and deliberately free of call/SMS automation: the teacher received the call, the app just records it. No contacts sync, no group registry — the share sheet *is* the parent distribution layer until a server phase proves otherwise.

## Connections
- **Consumes:** `data-repositories` from `core-data` — Announcement/AnnouncementTemplate/ContactLogEntry rows plus Student names; `class-student-picker` from `roster` — target class for announcements, target student for log entries; `share-helpers` from `app-shell` — text and file-capable share actions (signature agreed across the seam at build time, same as `homework`).
- **Produces:** `parents-screen` — Parents tab root composable (typed params + callbacks only).
- **Talks to via:** in-process composables; routes wired by `app-shell`, picking delegated to `roster`'s picker component.

## Open questions
- None. (How parents are reached today — WhatsApp/SMS/calls — is README open question #10 and only informs Phase 0 interviews; the share sheet already covers every one of those channels.)

## Build notes for the implementing agent
- Reuse `app-shell`'s `FileProvider` for sharing generated PDFs — do not declare another provider (coordinate if something appears missing).
- Render PDF text with `StaticLayout`-style layout so content typed in Hindi or other complex scripts wraps and displays correctly — UI language is English-only, but announcement *content* is whatever the teacher types.
- Templates are ordinary teacher-editable rows: in-place rename/delete, recent-first ordering, no settings screen.
- Copy-as-text goes through the system clipboard — plain text, no permission, no PDF fallback.
- Never build contacts, groups, or automatic sending; if the client asks for absence alerts, that's the post-prototype server conversation.
- Deleting a template must not touch past `Announcement` rows, and deleting an announcement must not touch its templates.

## Build progress
- **Owner:** opencode (lead + builder) — DONE 2026-10-21.
- **Coded:**
  - `parents/pdfstore/AnnouncementPdfStore.kt` — `PdfDocument` A4 renderer; `StaticLayout` wrapping (complex-script safe), line-band pagination for multi-page bodies, title/context/divider header on page 1; writes `<filesDir>/exports/announcement_<millis>.pdf` (already in `file_paths.xml` — no seam change needed)
  - `parents/viewmodel/ParentsViewModel.kt` — `ParentsViewModel` (tab: announcements + contact log observed off repositories, recent-first, precomputed date labels, add/delete entry, delete announcement) + `AnnouncementEditViewModel` (homework-style commit-as-typed Announcement row, discard-on-abandon-unless-shared, template apply/save-as/update/delete with `markTemplateUsed`, PDF render on IO → `Event.SharePdf`) + manual-DI providers (`parents-tab`, `parents-announcement-edit`)
  - `parents/ui/ParentsScreen.kt` — tab root (`parents-screen`): announcements section (tap row = re-share text; MoreVert → delete-with-confirm) + contact-log section (roster `StudentPicker` → add dialog; per-row delete-with-confirm); recent-first caps 10/15 with "showing last n of m" honesty lines; empty/loading states; FAB "New announcement"; compose takeover hosted here (homework pattern, tab route)
  - `parents/ui/AnnouncementEdit.kt` — optional class via `ClassPicker` (+ Clear), title, body (minLines 5, required to share), Share-as-PDF + Copy-text row, Templates section, bottom-anchored primary Share; back/close discards unshared draft (VM `close()`); template apply confirms before overwriting a dirty draft; clipboard copy is plain text (card note)
  - `parents/ui/TemplateChips.kt` — recent-first `FilterChip`s, tap = apply, long-press = in-place edit dialog (name + message, delete), AssistChip "Save as template" prefilled from draft; TagChips wrapper pattern (tap never double-fires)
  - `parents/ui/ContactLog.kt` — `AddContactEntryDialog` (date defaults today, backdatable via M3 DatePicker — yesterday's call is a real case) + `ContactEntryRowContent`
  - `nav/AppNavHost.kt` (app-shell seam, wired as lead) — PARENTS placeholder replaced with `ParentsScreen()`; the only cross-module file in this commit
- **Remaining:** on-device QA (no device/emulator here): template overwrite confirm, PDF share via share sheet on a real device, clipboard behavior on API 33+, backdate picker, delete confirms.
- **Blocked on:** nothing.
- **Handoff note:** `exports/` is now shared between parents (announcement PDFs) and the future export-backup module — sweep ownership belongs to export-backup (pdfstore KDoc says so). PDFs are derivable artifacts: the Announcement row is the source of truth, abandon deletes the row only. No new dependencies; no `file_paths.xml` change; no internet permission. Template delete never touches Announcement rows (repository enforces; card build note honored). `StaticLayout.Builder.setIncludeFontPadding` does not exist on the current compile SDK — removed, default applies.
- **Last checkpoint:** 2026-10-21 (DONE)
