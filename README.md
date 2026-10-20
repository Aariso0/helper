# Teacher Classroom App (Android)

> **Status:** Research / prototype planning. **No code written yet.**
> **Last updated:** 2026-10-20
> **Current focus:** a **very efficient, easy, time-saving UI**. Nothing else matters yet.
> **Server / cloud:** **NOT being built.** See [Skip for now](#skip-for-now).

Items marked **UNKNOWN** or **TO VERIFY** were not confirmed during research. Do not treat them as facts.

---

## 1. Project summary

An Android app for one teacher (the client) with five features:

1. Attendance
2. Student feedback
3. Parent information space
4. Parent feedback
5. Homework assignment (text, image, PPT)

Agreed direction:

- The client wants a **prototype first**. The client says they can arrange a server **after** seeing the prototype.
- So the prototype is a **local, on-device app**. No server, no login, no sync.
- The prototype exists to prove one thing: **it is faster and easier than the client's Excel sheet.**

---

## 2. Priority #1: UI efficiency

Every decision in the prototype is judged by one question:

> **Does this save the teacher time or taps in daily use?**

If a feature, screen or option does not clearly pass, cut it or hide it.

### 2.1 Design rules

1. **Fewest taps.** The most frequent task (attendance) must take the fewest taps of any task in the app.
2. **Smart defaults over choices.** Preselect today's date, the most-used class, "Present" for everyone. The teacher should only act on exceptions.
3. **Home screen = today.** Opening the app shows what the teacher needs right now, not a menu of features.
4. **One-handed, phone-first.** Big tap targets, primary actions at the bottom of the screen (thumb reach), no tiny cells or horizontal scrolling.
5. **Never lose work.** Autosave. Easy undo. Easy edit of past entries.
6. **Works with no internet.** (Prototype is fully local anyway.)
7. **Quick-add everywhere.** Preset tags, templates, "same as last time" options, recent items first.
8. **Readable at a glance.** Counts and status visible without opening anything (e.g. "3 absent today").
9. **No setup wall.** Importing the existing student list from Excel/CSV beats typing 40 names.
10. **Export stays possible.** CSV/Excel and PDF export so the client never feels locked in.

### 2.2 Proposed measurable targets (to be tested, not facts)

These are **proposals** to validate with the client using a stopwatch against their current Excel routine:

| Task | Proposed target |
|---|---|
| Take attendance for a full class | Under 30 seconds, from opening the app |
| Fix a wrong attendance entry from a past day | Under 15 seconds |
| Add a feedback note for a student | Under 10 seconds with a preset tag |
| Send homework to a class | Under 1 minute including an attachment |
| Find a student's full history | 2 taps from home |

**UNKNOWN:** the client's current times in Excel. Measure them first so there is a real baseline.

### 2.3 Why this can beat Excel

Excel is flexible, free and familiar, so the app only wins on speed and convenience. Be honest with the client about that.

Where Excel is weak for this job:

- Awkward on a phone (tiny cells, zooming, scrolling across a wide grid).
- Marking 30-60 students needs many taps or keystrokes.
- No per-student history view without building formulas.
- Feedback notes become long text in cells, hard to find later.
- Homework files live somewhere separate from the class list.
- Easy to break (wrong sort, overwritten formula, deleted row).

Patterns seen in existing attendance apps (see section 13) that are worth copying:

- **Tap only the absentees.** TeachVault's changelog describes untouched students being counted as present on save.
- **One-tap marking and offline use.** Advertised by Present+.
- **Roster import from Excel/CSV with a preview** (TeachVault).
- **Exports to CSV/Excel** (iRollCall).

### 2.4 Alternatives to check before building (not researched)

If an existing tool already fits, building may not be justified.

- Google Classroom, Google Sheets + Forms, WhatsApp groups, school ERP apps: **not researched. TO VERIFY.**

---

## 3. Proposed screen map (prototype)

A proposal to react to, not a final design.

**Bottom navigation (4 items):**

| Tab | What it is | Notes |
|---|---|---|
| **Today** (home) | Today's classes. One tap on a class opens attendance | Shows "attendance not taken yet" nudges and pending homework due |
| **Students** | Class list -> student list -> student page | Search at the top |
| **Homework** | Assigned homework per class + "New homework" | |
| **Parents** | Announcements + parent contact log | Shared manually in the prototype |

### 3.1 Attendance screen (the most important screen)

- Header: class name, date (default today, tap to change), live counter: "Present 28 | Absent 2".
- Student list, **all preset to Present**.
- **Tap a student = mark absent.** Tap again = back to present. Long-press = Late / Excused.
- Search/jump bar for large classes (**UNKNOWN** class size).
- Big **Save** button at the bottom. After saving: "Share absentee list" shortcut.
- Editing a past date uses the same screen.

### 3.2 Student page

- Name, photo (optional), roll number, parent contact (tap to call or message).
- Attendance % and recent absences.
- Feedback timeline with a big **+ Quick note** button (preset tags + optional text).
- Homework status (if tracked, **UNKNOWN** whether needed).

### 3.3 Quick feedback

- From the student page or a quick list: pick preset tag(s) -> optional one line -> done.
- Tags are editable by the teacher. Search/filter by student, tag, date.

### 3.4 Homework

- Choose class -> title -> text -> attach photo/file -> due date -> **Share**.
- Camera shortcut: photograph a whiteboard/page and attach in one step.
- Sharing uses the Android share sheet (WhatsApp, etc.) in the prototype.
- **PPT note:** Android has no built-in PPT viewer. Open PPTs in an external app, or ask for PDF. **Decision needed.**

### 3.5 Parent information and parent feedback (prototype behaviour)

- **Parent information:** teacher writes an announcement (templates for common ones) -> share via the share sheet to a parent group, or copy as text/PDF.
- **Parent feedback:** teacher **logs** feedback received by call or message against the student. No parent-facing screen exists in the prototype.
- True in-app parent access needs a server, which is skipped (see below).

### 3.6 Reports

- One-tap monthly attendance summary per class and per student.
- Export to CSV/Excel and PDF.

---

## Skip for now

**Everything below is NOT built in the prototype. Do not start it.** The client plans to get a server after seeing the prototype.

### Do not build or add

- Any backend or server code
- Firebase, Supabase or any other cloud SDK/dependency
- User accounts, login, signup, password reset
- Data sync, background sync workers, conflict resolution
- Cloud file storage (homework attachments stay on the device)
- Parent-facing app/screens, parent login, parent roles
- Push notifications and automatic absence alerts
- Real-time features
- Cloud backup (use manual export for now)
- Internet permission, unless a feature truly needs it (**TO VERIFY** per feature)
- Play Store publishing (share the APK for the prototype; **TO VERIFY** details)
- Multi-teacher or multi-school support

### What this means for the features

| Feature | In the prototype |
|---|---|
| Attendance | Full, local |
| Student feedback | Full, local |
| Parent information space | Teacher composes and **shares manually** |
| Parent feedback | Teacher **logs it manually** |
| Homework | Created locally, **shared manually** |

### Optional low-cost habits (not server work, only if free to do)

These cost almost nothing now and make the later server step easier. Skip any that slow the prototype:

- Give every record a unique ID generated on the device.
- Store created/updated times on records.
- Keep data access behind one layer so the UI does not depend on where data lives.

### Parked research (for when the server phase starts)

Kept for later. **Prices change; TO VERIFY on official pages before deciding.**

**Architecture idea:** "offline-first". A local database (Room) is the source of truth, and a background worker (WorkManager) syncs to a server when online. Common simple conflict rule: Last-Write-Wins.

**Firebase**
- Spark (free) needs no card. Blaze is pay-as-you-go.
- Since **3 Feb 2026**, Cloud Storage for Firebase requires the Blaze plan even at tiny usage. Homework file uploads would need a billing account.

**Supabase**
- Free: 2 projects, 500 MB database, 1 GB file storage, 5 GB egress, 50,000 monthly active users.
- Free projects **pause after 7 days of inactivity**.
- Pro from $25/month, no pausing, 8 GB database, 100 GB storage.

**Own server:** more control, more maintenance. Cost and skills **UNKNOWN**.

**Decision:** none. The client will decide once the prototype is seen.

---

## 4. Feature breakdown (prototype scope)

### 4.1 Attendance
- Classes, students, date, optional period.
- Statuses: Present, Absent, Late, Excused. Optional note per absence.
- **UNKNOWN:** one class or many? One period per day or several? Required school format?

### 4.2 Student feedback
- Notes per student with date, tag, text.
- **UNKNOWN:** should students/parents ever see them? Prototype: teacher-only.

### 4.3 Parent information space
- Announcements the teacher prepares and shares manually.

### 4.4 Parent feedback
- Teacher-entered log of what parents said.

### 4.5 Homework
- Text, image, PPT (and PDF recommended), due date, shared manually.

---

## 5. Suggested tech stack (proposal, not decided)

| Area | Proposal | Reason |
|---|---|---|
| Language | Kotlin | Standard for Android |
| UI | Jetpack Compose | Current standard UI toolkit, fast to iterate UI |
| Local storage | Room | Standard on-device database |
| Architecture | MVVM | Common, simple |
| Min SDK | **UNKNOWN** | Depends on the client's phone. Ask which Android version they use |

Not in the prototype: network libraries, WorkManager sync, any cloud SDK.

**UNKNOWN:** whether to start with fake in-memory data for UI demos before wiring storage. For client testing, real local storage is more convincing.

---

## 6. Data model (draft, local)

| Entity | Key fields (conceptual) |
|---|---|
| Class | name, subject, year |
| Student | name, roll no, class, parent contact, optional photo |
| AttendanceRecord | student, date, status, note |
| Feedback | student, text, tag, date |
| FeedbackTag | label (teacher-editable) |
| Announcement | class, title, body, date |
| ParentContactLog | student, text, date |
| Homework | class, title, text, due date |
| Attachment | homework, type (image/ppt/pdf), file path |

Removed for now: Teacher account, Parent account, sync fields.

---

## 7. Privacy and safety (children's data)

- **Client's country: UNKNOWN.** Rules depend on it.
- If **India**: the DPDP Act 2023 treats under-18s as children and generally requires verifiable parental consent. Educational institutions get a limited, conditional exemption. Whether an individual teacher or tutor counts is **unclear** in the sources found. Enforcement timing: **TO VERIFY**. Not legal advice.
- Other countries (US COPPA/FERPA, EU GDPR) **not researched**.

Prototype safeguards:

- Data stays on the phone. The main risk is **device loss or reset**, so provide manual export/backup.
- No real student data in the GitHub repo, screenshots or demo files. Use fake names.
- Optional phone/app lock.
- A way to delete a student's data.

---

## 8. Open questions for the client (UI-focused first)

1. Can we see the current Excel sheet (with fake data)? Which columns and reports do they actually use?
2. How long does attendance take today? (Baseline for the targets in 2.2.)
3. How many classes and students? How many classes per day?
4. What phone and Android version does the client use?
5. What does the teacher do most often during the day, and what is most annoying today?
6. Is attendance needed in a specific format for the school/principal?
7. What preset feedback tags would be useful? (Collect real examples.)
8. PPT: is attaching PDF acceptable?
9. UI language: English only, or Hindi/other?
10. How are parents reached today (WhatsApp, SMS, calls)?

---

## 9. Risks

- **Scope creep:** the prototype must stay small. Resist adding server-ish features.
- **Data loss on the device** without backup.
- **Prototype mistaken for finished product:** agree up front that it is a prototype.
- **Server phase rework:** low if the optional habits in "Skip for now" are followed, but not guaranteed.
- **Maintenance:** someone must fix bugs and handle Android updates. **UNKNOWN** who.

---

## 10. Roadmap

**Phase 0: Validate (no code)**
- Get the Excel sheet and the answers in section 8. Measure the baseline. Check alternatives (2.4).

**Phase 1: Core UI prototype**
- Today screen, attendance screen (tap-absentees flow), students list + import from Excel/CSV.
- Test with the client. Compare against the stopwatch targets.

**Phase 2: Remaining features, local**
- Quick feedback, homework with attachments and sharing, parent announcements and contact log, reports and export, manual backup/restore.
- Refine based on the client's daily use. Remove anything unused.

**Later (not now): see [Skip for now](#skip-for-now)**
- Server, login, sync, parent access.

---

## 11. Android Studio setup (new project)

Menu names can differ slightly between versions. **TO VERIFY** against your installed version.

1. Install the current stable **Android Studio** from developer.android.com/studio.
2. Complete the first-launch setup wizard so the Android SDK installs.
3. **New Project** -> **Empty Activity** template (Jetpack Compose).
4. Fill in:
   - **Name:** e.g. `TeacherClassroomApp`
   - **Package name:** e.g. `com.yourname.teacherapp` (hard to change later)
   - **Save location:** a folder you will remember
   - **Language:** Kotlin
   - **Minimum SDK:** decide after asking the client's phone question. Until known, pick a commonly supported older version.
   - **Build configuration language:** Kotlin DSL (default)
5. Click **Finish** and wait for the first **Gradle sync** (can take several minutes).
6. Create an **emulator** (Device Manager -> Create Device) or connect a real phone with **USB debugging** on, then press **Run** to confirm the blank app launches.
7. Add this README to the project (section 12) before writing features.

---

## 12. Where to put this README.md

Put it in the **project root folder**: the same folder that contains `settings.gradle.kts`, the `app` folder and `.gitignore`.

Not inside `app/` and not inside `app/src`.

- Android Studio's left panel defaults to the **Android** view, which hides the root. Switch the dropdown at the top of the panel to **Project**.
- Copy `README.md` into the root with your file manager, or drag it into the Project panel.
- If a `README.md` already exists, replace it.

GitHub shows `README.md` on the repository front page automatically.

---

## 13. Upload to GitHub

Recommended: make the repository **Private** (client project).

### On GitHub

1. Sign in -> **New repository**.
2. Name it (e.g. `teacher-classroom-app`), choose **Private**.
3. **Do not** tick "Add a README", ".gitignore" or "license". Your project already has them and ticking them causes a conflict on first push.
4. Click **Create repository** and keep the page open (it shows the repo URL).

### On your computer (Android Studio: bottom panel -> Terminal, in the project root)

Git must be installed.

1. `git init`
2. `git add .`
3. `git commit -m "Initial commit: project skeleton and research README"`
4. `git branch -M main`
5. `git remote add origin <your repository URL>`
6. `git push -u origin main`

### Authentication

GitHub no longer accepts account passwords for Git pushes. Use one of:

- Android Studio: Settings -> Version Control -> GitHub -> add account (browser sign-in)
- GitHub CLI (`gh auth login`)
- A Personal Access Token entered when Git asks for a password

### Before you push

- The new-project template normally includes a `.gitignore` that excludes build output and `local.properties`. **TO VERIFY** it is present.
- **Never commit:** passwords, API keys, signing keystores (`.jks`/`.keystore`), real student data.

### Later workflow

`git add .` -> `git commit -m "what changed"` -> `git push`. Android Studio also offers commit/push from the Git menu / Commit panel.

---

## 14. Sources consulted

Existing attendance/teacher apps (UI patterns):
- https://apps.apple.com/us/app/present/id6742638739 (Present+: one-tap, offline)
- https://apps.apple.com/dk/app/id6756511517 (TeachVault: tap only absentees, CSV/Excel import)
- https://rv.teachngo.com/features-new/school-attendance-software (Teach 'n Go: parent absence notifications)
- https://www.adalo.com/use-cases/attendance-app/ (SheetBridge: spreadsheet-backed)
- https://www.producthunt.com/posts/irollcall (iRollCall: QR check-in, CSV export)

Parked (server phase) architecture:
- https://proandroiddev.com/jetpack-compose-offline-first-architectures-5495ec6ddfa8
- https://dev.to/mohan_sankaran/offline-first-android-build-apps-that-keep-working-when-the-network-doesnt-3m39
- https://dev.to/odunayo_dada/offline-first-mobile-app-architecture-syncing-caching-and-conflict-resolution-1j58
- https://github.com/LuongHo-Dev/Offline-first-Notes
- https://github.com/DaCodaHUB/JobTracker

Parked (server phase) pricing:
- https://dev.to/androve2k/spark-vs-blaze-the-firebase-pricing-guide-i-wish-id-read-sooner-onb
- https://www.verdent.ai/guides/devtools/firebase-pricing
- https://www.jetadmin.io/blog/supabase-pricing-2026-guide-to-plans-limits-and-real-world-costs/
- https://www.cloudzero.com/blog/supabase-pricing/
- https://neon.com/guides/neon-vs-supabase-free-plan

Privacy (India):
- https://www.ey.com/en_in/insights/cybersecurity/transforming-data-privacy-digital-personal-data-protection-rules-2025
- https://dpdpa.com/dpdparules/rule12.html
- https://www.livelaw.in/amp/articles/draft-digital-personal-data-protection-rules-2025the-goal-remains-distant-284283

These are secondary sources (blogs, store listings, summaries). Verify pricing and legal facts on official pages before relying on them.