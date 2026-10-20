# Glossary — one name per concept

Created lazily during grilling. Module slugs and `consumes` / `produces` interface names use these terms. Sharpen an entry in place when a fuzzy term is resolved.

- **data-repositories** — the set of repository objects owned by `core-data` that all feature modules use to read and write; the project's "one layer" between UI and storage. *Not:* DAOs, Room, or the database directly (those are internal to `core-data`).
- **Class** — a named teaching group (e.g. "5-A"); the unit attendance and homework are organized around. *Not:* "grade" or "section" used interchangeably.
- **Student** — one child record (name, roll number, parent contact, optional photo) belonging to exactly one Class. *Not:* "pupil", "child" in code or cards.
- **Attendance record** — one student's status for one class-day: Present / Absent / Late / Excused, with an optional note and optional period. *Not:* "attendance session" for a single row; a *session* is the act of taking the class's attendance on a date.
- **Feedback note** — one teacher-authored observation on a student (tag + optional one-line text + date). *Not:* "comment"; student feedback and parent feedback are different concepts — parent feedback lands in the **Contact log entry**.
- **Contact log entry** — one teacher-logged instance of parent communication (call/message received), against a student. *Not:* "parent feedback record".
- **Announcement** — one teacher-composed message intended for a parent group, shared manually via the share sheet. *Not:* "notification" — nothing is pushed.
- **Homework** — one assignment for a class (title, text, due date, attachments). *Not:* "task" or "assignment" used as module names.
- **Attachment** — one file hung on a Homework: image, PDF, or PPT, stored on device. *Not:* "upload" — nothing leaves the device.
- **share sheet** — Android's system share dialog (ACTION_SEND); the prototype's only "sending" mechanism. *Not:* "send", "sync", or "publish".
- **class-student-picker** — the reusable Compose UI (owned by `roster`) for choosing a Class or a Student, used by features that pick outside the Today screen. *Not:* each feature inventing its own list screen.
- **photo-storage** — the app-internal directory convention where picked student photos are copied and their paths recorded. *Not:* "gallery" or "media store" — nothing is written to shared storage.
- **screen(s)** — `{module}-screen(s)`: the root composables a feature module exports for `app-shell`'s navigation graph, taking only typed parameters and callbacks. *Not:* routes or URLs — route strings belong to `app-shell` alone.
- **share-helpers** — the ACTION_SEND intent builders owned by `app-shell` that feature screens call to open the system share sheet with prepared text or files. *Not:* sending, syncing, or any background delivery — sharing is always user-initiated.
- **app-theme** — the Material 3 color/type/sizing theme applied at the composition root by `app-shell`; feature screens render under it ambiently. *Not:* per-screen styling that fights the fewest-taps sizing rules.
- **Today screen** — the home tab: today's classes with "attendance not taken" nudges, pending-homework counts, and one tap into attendance. *Not:* a menu of features or a dashboard of charts.
- **feedback-timeline** — the chronological Feedback notes list (owned by `student-feedback`) embedded in `roster`'s student page; its + button emits an `onQuickNote` callback rather than navigating. *Not:* a separate screen or a second feedback data path.
- **attachment-storage** — the app-internal directory where picked or captured homework files are copied and their paths recorded (convention owned by `homework`). *Not:* shared storage, MediaStore, or any cloud path.
- **AnnouncementTemplate** — a teacher-authored reusable announcement body (title + text), stored as rows so "templates for common ones" (README 3.5) works from day one. *Not:* copies of past date-specific announcements; those are `Announcement` rows.
