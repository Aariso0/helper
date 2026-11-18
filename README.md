<a id="top"></a>
<p align="center">
  <img src="logo.png" alt="Teacher Classroom App logo" width="120" height="120">
</p>

<h1 align="center">Teacher Classroom App (Android)</h1>

<p align="center">
  A local, on-device app for one teacher — attendance, homework, feedback and exam results, faster than an Excel sheet.
</p>

<p align="center">
  <a href="https://github.com/Aariso0/helper/issues">Report Bug</a> ·
  <a href="https://github.com/Aariso0/helper/issues">Request Feature</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF.svg" alt="Language: Kotlin">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84.svg" alt="Platform: Android">
  <img src="https://img.shields.io/github/stars/Aariso0/helper.svg" alt="Stars">
</p>

## Table of Contents

- [About](#about)
- [Features](#features)
- [Built With](#built-with)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

## About

This is an Android app for a single teacher who wants daily classroom tasks done in fewer taps than an Excel sheet. It covers attendance, student feedback, homework, parent information, roster, exam results, reports and backup, all stored locally on the device.

Version 1.3.0 is a local prototype: no login, no sync and no `INTERNET` permission in the manifest. It works fully offline.

> **TODO: add screenshot** — no screenshots or demo GIFs exist in the repo yet. A placeholder directory is reserved at `docs/screenshots/`.

[↑ Back to top](#top)

## Features

- **Attendance** — tap only absentees; long-press for Late/Excused; edit past dates; live counters and undo.
- **Student feedback** — quick notes with editable preset tags and a per-student timeline.
- **Homework** — assignments with image/file attachments, due dates and Android share-sheet sharing.
- **Parents** — announcement templates and a manual parent contact log.
- **Roster** — classes and student profiles with photos; Excel/CSV import with sheet and row-range selection; academic sessions.
- **Exams & results** — create or import an exam, enter marks one subject at a time, mark absentees `ABT`, and get totals, percentages, pass/fail and rank.
- **Excel export** — Results sheet with live formulas, plus Summary and Subject Teacher sheets; export columns are configurable.
- **3-page result PDF** — portrait marks statement (identity columns repeated, continuation pages for long classes), landscape class summary and landscape subject-teacher details.
- **Reports & backup** — monthly CSV/XLSX/PDF reports and a zip backup/restore with manifest validation.
- **Offline** — Room database on device; no network permission, no cloud dependency.

[↑ Back to top](#top)

## Built With

| Component | Technology | Version |
| --- | --- | --- |
| Language | Kotlin | 2.3.21 |
| UI | Jetpack Compose, Material 3 | BOM 2026.09.00 |
| Architecture | MVVM + Room + Coroutines | Room 2.8.5, Coroutines 1.11.0 |
| Navigation | Navigation Compose | 2.10.2 |
| Build | Android Gradle Plugin, Gradle | 9.5.0, 9.6.0 |
| Import & images | Apache Commons CSV, kexcel-android, Coil | 1.14.1, 0.1.2, 3.6.3 |
| PDF | Android platform `PdfDocument` + `Canvas` | part of the Android SDK |
| Min / target SDK | Android 7.0 / API 37 | `minSdk 24`, `targetSdk 37` |

[↑ Back to top](#top)

## Getting Started

### Prerequisites

- JDK 17 (CI uses Temurin 17)
- Android SDK platform API 37
- Android Studio (optional — the Gradle wrapper is enough)

### Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/Aariso0/helper.git
   cd helper
   ```

2. Compile, build and test (the project gate):

   ```bash
   ./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest --console=plain
   ```

3. Install the debug APK output on a connected device:

   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

Release builds need signing — see [Configuration](#configuration).

[↑ Back to top](#top)

## Usage

- **Start**: the app opens to a Today home screen with bottom navigation.
- **Attendance**: open a class, tap the students who are absent (everyone starts Present), and use the snackbar to undo the last action.
- **Exams**: create an exam or import one from an Excel sheet, then enter marks subject by subject. The results screen shows totals, percentages, pass/fail and rank.
- **Export the PDF**: open an exam's results and choose **Export as PDF** from the overflow menu. The app writes the three-page PDF to its cache and opens the Android share sheet so you can print or save it.
- **Export Excel**: the exam results screen also writes a workbook with Results, Summary and Subject Teacher sheets.
- **Homework and reports**: attach files or share homework through the system share sheet; generate monthly reports or a zip backup from the export/backup screen.

[↑ Back to top](#top)

## Project Structure

<details>
<summary>Expanded source tree</summary>

```text
helper/
├── app/src/main/java/com/example/helper/
│   ├── MainActivity.kt          # launcher activity
│   ├── coredata/                # Room database, entities, DAOs, repositories
│   ├── roster/                  # classes, students, sheet/row import
│   ├── attendance/              # attendance marking
│   ├── studentfeedback/         # feedback notes and tags
│   ├── homework/                # assignments and attachments
│   ├── parents/                 # announcements and contact log
│   ├── examsresults/            # exams, mark entry, Excel/PDF export, import
│   ├── exportbackup/            # reports and backup/restore
│   ├── scaffold/, nav/, theme/  # app shell, routes, theming
│   └── share/, today/, motion/  # share helpers, home screen, motion
├── app/src/main/res/            # theme, strings, launcher icons
├── app/src/test/                # JVM unit tests
├── modules/                     # blueprint cards (docs only)
├── _index/                      # module status and connections
└── .github/workflows/           # ci.yml and release.yml
```

</details>

[↑ Back to top](#top)

## Configuration

Debug builds need no secrets. Release builds do:

- A gitignored `keystore.properties` file in the project root:

  ```properties
  storeFile=/path/to/your/keystore.jks
  storePassword=your_store_password
  keyAlias=your_key_alias
  keyPassword=your_key_password
  ```

- Or the equivalent environment variables, which take precedence: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

CI uses `ci.yml` on pushes and pull requests to `main` (tests, debug build, Kotlin compile), and `release.yml` on `v*` tags, which needs the `production` environment plus the secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`. See [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

[↑ Back to top](#top)

## Roadmap

From the module status tracker in [`_index/MODULES.md`](_index/MODULES.md):

- **Animations** — spring motion system drafted but not started; will touch every built screen.
- **App shell** — marked needs-rework until the animations pass lands.
- **Server comms** — planned v3 placeholder, not researched or built.

[↑ Back to top](#top)

## Contributing

1. Fork the repository.
2. Create a branch: `git checkout -b feature/my-change`.
3. Commit your change and push the branch.
4. Open a pull request to `main`. CI runs on pull requests to `main`.

[↑ Back to top](#top)

## License

MIT — Copyright (c) 2026 helper contributors. See [LICENSE](LICENSE) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

[↑ Back to top](#top)

## Contact

[Aariso0 on GitHub](https://github.com/Aariso0) · [Issues](https://github.com/Aariso0/helper/issues)
