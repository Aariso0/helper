---
module: core-data
status: drafted   # planned · researching · drafted · needs-redo
consumes: []      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["data-repositories"]      # what this module exposes to others (use _shared/glossary.md terms)
build: built   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-20
---

# Core data

The single local data layer: Room database, entities, and repositories every feature goes through. Provides device-generated IDs, created/updated timestamps, and the one-layer seam that keeps a later server phase cheap.

## # In Use
- **Room 2.8.5** — official Android persistence library; stable line, SQLite wrapped with compile-checked queries.
- **KSP** — official replacement for kapt, which is in maintenance mode; required for fast Room codegen.
- **Manual `AppContainer` DI** — Google's documented manual-DI pattern for small apps; zero build-time machinery.
- **Repository pattern** — the README's "one layer" habit: no feature module touches Room directly.

## # Excluded
- **Room 3.0 alpha** — first alpha (March 2026) is a KMP rewrite dropping SupportSQLite; alpha risk with zero benefit for an Android-only prototype. Runner-up that lost a close call.
- **kapt** — Google has it in maintenance mode and recommends KSP for all processors.
- **Hilt (2.60.1)** — annotation-processing overhead and boilerplate outweigh the gains at single-module, ~8-feature prototype scale.
- **Koin (4.2.2)** — a framework dependency (global service-locator state) for what the official manual-DI pattern covers with one container class.
- **SQLDelight / raw SQLite** — Room is the Android standard the README proposes; hand-rolled SQL and migration bookkeeping buy nothing here.
- **RxJava for async** — coroutines + Flow are Room's native API and the project's Kotlin-first choice.
- **`room-ktx`** — merged into `room-runtime` since 2.7; the artifact is blank and must not be added.
- **desugared `java.time` for timestamps** — avoided entirely by storing epoch millis as `Long` (see Exact stack); no desugaring dependency, works on any min SDK the client's phone turns out to have.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| Database | `Room` | `2.8.5` | official Android ORM, stable Sep 2026 line; 3.0 is alpha KMP rewrite |
| Annotation processing | `KSP` | `2.3.12` | kapt in maintenance mode; KSP2 line, Room fully supports it |
| Room Gradle plugin | `androidx.room` | `2.8.5` | reproducible schema export (`schemaDirectory`) for real migrations |
| Async API | `kotlinx-coroutines` + `Flow` | `1.11.0` | Room's native surface: `Flow` for lists, `suspend` for writes |
| Dependency injection | manual `AppContainer` | — (no framework) | fewest moving parts; official manual-DI guidance for small apps |
| ID generation | `java.util.UUID` (`randomUUID`) | Kotlin/JDK stdlib | unique on-device, merge-safe if a server arrives later; zero deps |
| Timestamps | epoch millis `Long` (UTC) | — | sortable, comparable, no `java.time`/desugaring, API-level-proof |
| Schema evolution | explicit Room `Migration` objects | — | transparent, reviewable upgrade paths; never destructive-migrate user data |

## File & folder layout
```
core-data/
├─ AppDatabase.kt        Room database class: entity list, version, migrations
├─ AppContainer.kt       manual DI container, built once on the Application
├─ entity/               all @Entity classes (Class, Student, AttendanceRecord, FeedbackNote, FeedbackTag, Announcement, AnnouncementTemplate, ContactLogEntry, Homework, Attachment)
├─ dao/                  one DAO per aggregate, Flow + suspend queries only
├─ repository/           the data-repositories feature modules consume
└─ migration/            explicit Migration objects, versioned in order
```
Relative to the app module's main source set, under the project's root namespace. Entity names follow `_shared/glossary.md`.

## Setup sequence
1. In the top-level Gradle build file, declare plugins: `androidx.room` version `2.8.5` and `com.google.devtools.ksp` version `2.3.12`, both `apply false`.
2. In the app module's build file, apply both plugins, add `room { schemaDirectory("$projectDir/schemas") }`, and add dependencies: `implementation("androidx.room:room-runtime:2.8.5")`, `ksp("androidx.room:room-compiler:2.8.5")`, `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")`.
3. Create the `entity/` classes from the README data model (name fields in glossary terms; each with `id: String` UUID PK, `createdAt`/`updatedAt` epoch millis; `AttendanceRecord` carries the optional `period` field and one of the four statuses), plus `AnnouncementTemplate` (title, body) — requested by `modules/parents.md`, not in README §6, added before first build so schema v1 needs no migration.
4. Create `AppDatabase` (all entities registered, `version = 1`) plus the `dao/` files, then build once so Room exports schema JSON.
5. Commit the generated `schemas/` directory to git — it is the baseline for every future migration.
6. Create `AppContainer` on the `Application` class (owns the database + repositories), and create the `repository/` implementations.

## Why this shape
Everything downstream depends on this card being boring and stable, so each choice favored the path with the fewest moving parts. Room over SQLDelight or raw SQLite because it is the Android standard, the README already proposes it, and compile-checked DAOs catch schema drift at build time. KSP over kapt because Google has explicitly put kapt in maintenance mode and Room's own docs prescribe KSP. Room stayed on the stable `2.8.5` line rather than the new `3.0` alpha: 3.0 is a Kotlin-Multiplatform rewrite that drops SupportSQLite — irrelevant upside for an Android-only prototype, with alpha-level breakage risk. DI is a manual `AppContainer` rather than Hilt or Koin: at single-module prototype scale the official manual pattern is one class, carries no annotation processing, and gives the agent build team one obvious seam to register repositories in. UUID string PKs and epoch-millis `Long` timestamps exist specifically for the README's "optional low-cost habits": unique IDs and timestamps that survive a later server merge, and timestamp storage that needs no `java.time` desugaring no matter what min SDK the client's phone ends up requiring. Repositories expose `Flow` for anything the UI observes (live counters, lists) and `suspend` for actions — this is Room's native shape and maps directly onto MVVM ViewModels. Schema changes are explicit `Migration` objects with exported schema JSON because "never lose work" is a design rule: destructive fallback migrations are banned in this project.

## Connections
- **Consumes:** nothing from other modules — this is the foundational card; built first.
- **Produces:** `data-repositories` — the repository objects for every entity (Class/Student, AttendanceRecord, FeedbackNote/FeedbackTag, Announcement, ContactLogEntry, Homework/Attachment), each exposing `Flow` reads and `suspend` writes. Feature modules receive these via `AppContainer`.
- **Talks to via:** in-process object references through the manual `AppContainer`; no module outside `core-data` touches DAOs, `AppDatabase`, or SQLite directly.

## Open questions
- KSP artifact line must match the Kotlin version already pinned by the project skeleton (the skeleton's version is not readable under this skill's no-code rule). If `2.3.12` doesn't match, pick the matching release from `github.com/google/ksp/releases` and flag it to the user — a Kotlin bump is a project-level change, not a quiet edit here.
- min SDK is still a client question (project-level `Unresolved`); nothing in this stack constrains it.

## Build notes for the implementing agent
- Run the setup sequence before any feature module starts — `data-repositories` is the contract every other card consumes.
- The KSP version must pair with the project's Kotlin version; do not bump Kotlin/AGP yourself to make it fit — `Open questions` + the lead.
- Never add `room-ktx` (blank since 2.7) and never enable destructive migration fallbacks.
- Exported schemas in `schemas/` must be committed; they are the input for future `Migration` objects.
- `AppContainer` lives on the `Application` class; feature ViewModels get repositories through it, never through constructors reaching for Room.

## Build progress
- **Owner:** opencode (lead + builder)
- **Coded:**
  - `gradle/libs.versions.toml` — added ksp `2.3.12`, room `2.8.5`, coroutines `1.11.0` + plugin/library entries (all verified on live Maven repos; KSP ≥2.3 versions independently of Kotlin, so 2.3.12 pairs with the skeleton's Kotlin 2.2.10 — resolves this card's Open question)
  - `build.gradle.kts` (root) — room + ksp plugins `apply false`
  - `app/build.gradle.kts` — plugins applied, `room { schemaDirectory("$projectDir/schemas") }`, room-runtime / room-compiler(ksp) / coroutines-android deps
  - `app/src/main/java/com/example/helper/coredata/entity/` — all 10 entities: `SchoolClass` (named `SchoolClass`, not `Class`, to avoid shadowing `java.lang.Class`), `Student`, `AttendanceRecord` (+ `AttendanceStatus` enum, unique (studentId, dateEpochDay) index, optional `period` kept null), `FeedbackTag`, `FeedbackNote` (tagId FK SET NULL + denormalized `tagLabel`), `Announcement` (classId SET NULL + denormalized `className`), `AnnouncementTemplate` (+ `lastUsedAt` for recent-first), `ContactLogEntry` (+ denormalized `studentName`), `Homework`, `Attachment` (+ `AttachmentKind` enum). All rows: UUID String PK + createdAt/updatedAt epoch millis.
  - `app/src/main/java/com/example/helper/coredata/Converters.kt` — enum↔String TypeConverters
  - `app/src/main/java/com/example/helper/coredata/dao/` — 7 DAOs (SchoolClass, Student, Attendance incl. `ClassDayCount`/`StudentStatusCount` projections for Today nudges + export, Feedback, Announcement, ContactLog, Homework incl. `HomeworkWithAttachments` @Relation); Flow reads + suspend writes only
  - `app/src/main/java/com/example/helper/coredata/AppDatabase.kt` — v1, all 10 entities, `exportSchema = true`, no destructive fallbacks
  - `app/src/main/java/com/example/helper/coredata/migration/Migrations.kt` — empty `ALL` registry, ready for v2
  - `app/src/main/java/com/example/helper/coredata/AppContainer.kt` + `HelperApplication.kt` + manifest `android:name` — manual DI on the Application
  - `app/src/main/java/com/example/helper/coredata/repository/` — 7 repositories (the `data-repositories` contract): UUID/timestamp stamping, `AttendanceRepository.commitSession`/`setStatus`/`setNote` per the attendance card's write model, `FeedbackRepository.addNote` commit-on-tap + `deleteNote` undo, `HomeworkRepository.deleteAttachment` returns the row so the caller deletes the file
  - `app/schemas/com.example.helper.coredata.AppDatabase/1.json` — exported baseline schema (committed; input for future migrations)
  - `app/src/main/java/com/example/helper/MainActivity.kt` — baseline repair: was a corrupted merge with a foreign flappybird project (committed broken in `bb27e08`); rewritten as a clean template so the project compiles. `app-shell` replaces it later.
- **Remaining:** none — every file in `File & folder layout` is coded.
- **Blocked on:** nothing
- **Handoff note:** `data-repositories` is live: feature modules get repositories via `(context.applicationContext as HelperApplication).container`. Dates everywhere are epoch-day `Long`s (`LocalDate.toEpochDay()`), never java.time in this layer. DAO queries that features may still need can be added freely (DAO changes are not schema migrations); schema changes need a `Migration` in `migration/` + version bump. Never enable destructive fallbacks.
- **Last checkpoint:** 2026-10-20T22:20+05:30
