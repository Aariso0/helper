# Teacher Classroom App (Android)

> **Status:** Build phase complete — 10 of 11 modules built. Local, on-device prototype.  
> **Last updated:** 2026-10-28  
> **Focus:** Fast, efficient, one-handed UI that beats Excel for daily classroom tasks.  
> **Server / cloud:** Not built (v3 placeholder only). Fully offline. Future server integration is planned in [modules/server-comms.md](modules/server-comms.md) as a self-hostable option for school/local servers (no reliance on third-party cloud providers).

---

## 1. Project Summary

A local, on-device Android app for a single teacher with the following features:

| Feature | Status | Description |
| --- | --- | --- |
| **Attendance** | Built | Tap-absentees marking with counters, past-date editing, undo support |
| **Student Feedback** | Built | Quick notes with editable preset tags, per-student timeline |
| **Homework** | Built | Create assignments with text/images/files, share via Android share sheet |
| **Parents** | Built | Announcements with templates, parent contact log (manual logging only) |
| **Roster & Students** | Built | Classes/students, student profiles, Excel/CSV import, photo support |
| **Export & Backup** | Built | Monthly reports (CSV/XLSX/PDF), full zip backup/restore with manifest preview |
| **Exams & Results** | Built | Exam creation, fast mark entry, totals/%, pass-fail, Excel export with formulas & conditional colors |
| **Animations** | Built | Spring-based motion system with shared elements and predictive back |
| **App Shell & Navigation** | Built | Today home screen, bottom nav, theme, share helpers |

All data is stored locally using Room. No login, no sync, no network permissions.

---

## 2. Core Principles

Every decision is judged by one question: **Does this save the teacher time or taps in daily use?**

- **Fewest taps** — Attendance is optimized for speed (tap only absentees, smart defaults)
- **Smart defaults** — Today's date preselected, everyone marked present by default
- **Home = Today** — App opens to what needs attention now
- **One-handed, phone-first** — Big tap targets, bottom-placed primary actions
- **Never lose work** — Autosave, undo support
- **Offline-first** — Works with no internet connection
- **Quick entry** — Presets, templates, recent items
- **At-a-glance info** — Counts and status visible without drilling in
- **Easy import** — Excel/CSV import for existing student lists
- **Stay in control** — Export to CSV/XLSX/PDF anytime

---

## 3. Tech Stack

| Component | Technology | Version |
| --- | --- | --- |
| **Language** | Kotlin | 2.3.21 |
| **UI** | Jetpack Compose + Material 3 | BOM 2026.09.00 |
| **Architecture** | MVVM + Room + Coroutines | Room 2.8.5 |
| **Navigation** | Navigation Compose | 2.10.2 |
| **Min/Target SDK** | Android 7.0+ / Android 15 | minSdk 24, targetSdk 37 |
| **Build Tools** | AGP 9.4.1, Gradle 9.6.0 | - |
| **Other** | KSP, Coil, Apache Commons CSV, kexcel-android | See `gradle/libs.versions.toml` |

---

## 4. Key Features

### Attendance
- Tap only absentees (students default to Present)
- Long-press for Late/Excused
- Edit past dates easily
- Live counters on screen
- Undo last action via snackbar

### Student Management
- Class and student organization
- Excel/CSV import with preview
- Student profile pages with photo support
- Full attendance/history timeline
- Exam history integrated into student page

### Homework
- Create assignments per class
- Attach images/files via SAF or camera
- Share via Android share sheet
- Track due dates

### Exams & Results
- Create exams with multiple subjects and per-subject max/pass marks
- Fast one-subject-at-a-time mark entry with numeric keyboard
- Auto-calculated totals, percentages, pass/fail
- Per-student analysis showing failed subjects
- Excel export with formulas and conditional coloring (Result column)
- Add/edit/delete exams and subjects with confirmations

### Backup & Export
- Monthly attendance reports (CSV, XLSX, PDF)
- Full app backup as zip with manifest
- Restore from backup with validation
- Share exports via system share sheet

---

## 5. Project Structure

```
helper/
├── app/src/main/java/com/example/helper/
│   ├── coredata/          # Room DB, entities, DAOs, repositories
│   ├── roster/            # Classes, students, import
│   ├── app-shell/         # Nav, theme, Today home
│   ├── attendance/        # Attendance marking
│   ├── studentfeedback/   # Feedback notes
│   ├── homework/          # Homework assignments
│   ├── parents/           # Announcements, contacts
│   ├── exportbackup/      # Reports, backup/restore
│   ├── examsresults/      # Exams & results
│   ├── animations/        # Motion system
│   └── nav/               # Navigation routes/host
├── modules/               # Blueprint cards (docs-only)
├── _index/                # Module status & connections
└── .github/workflows/     # CI/CD pipelines
```

---

## 6. Building & Running

### Prerequisites
- Android Studio (or command line)
- JDK 17
- Android SDK with API 37

### Build commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing - see below)
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Full check (compile, build, test)
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest
```

### Release Signing

For release builds, create a `keystore.properties` file in the project root:

```properties
storeFile=/path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Or set environment variables: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

See [RELEASE_SIGNING.md](RELEASE_SIGNING.md) for detailed instructions.

---

## 7. CI/CD

GitHub Actions workflows are configured:

- **`ci.yml`** — Runs on push/PR to `main`: compiles, tests, builds debug APK
- **`release.yml`** — Runs on tags `v*`: requires `production` environment approval, builds signed release APK, verifies signature, publishes to GitHub Releases

Required GitHub secrets for releases: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

---

## 8. Privacy & Security

- **All data stays local** — No cloud sync, no login, no analytics
- **No network permission** — App works fully offline
- **Secure signing** — Keystores excluded from Git, CI uses secrets securely
- **Backup safety** — Restore is file-level with validation, never reuses DB after restore
- **No PII in repo** — Student data, chat logs, and local configs are gitignored

---

## 9. Future: Self-Hostable Server Support

Server connectivity is **not implemented** in the current prototype. However, the app is designed with a clear path for future expansion:

- **Planned module:** [modules/server-comms.md](modules/server-comms.md) — Placeholder for v3 server integration
- **Self-hostable:** The future backend is intended to run on local school servers (LAN) with no dependency on third-party cloud providers
- **Offline-first remains** — Even with server support, the app will continue to work fully offline with local data
- **Opt-in only** — Server features will be completely optional; the app remains functional as a standalone local app
- **Data ownership** — Schools will retain full control of their data on their own infrastructure

**Status:** `planned` — Not researched or built yet. Will only be implemented when explicitly opened for v3 development.

---

## 9. Documentation

- [RELEASE_SIGNING.md](RELEASE_SIGNING.md) — Release signing and CI/CD setup
- [modules/](modules/) — Detailed blueprint cards for each module
- [_index/MODULES.md](_index/MODULES.md) — Build status tracker
- [_shared/](./_shared/) — Project constraints and glossary

---

## License

See [LICENSE](LICENSE) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for details.
