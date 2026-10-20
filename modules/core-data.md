---
module: core-data
status: drafted   # planned · researching · drafted · needs-redo
consumes: []      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["data-repositories"]      # what this module exposes to others (use _shared/glossary.md terms)
build: not-started   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
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
{Left blank by the blueprint skill. Implementing agents fill and update this after every chunk of work.}
- **Owner:** {agent / teammate name, or none}
- **Coded:**
  - {`path/to/file` — what is done in it}
- **Remaining** (next first):
  1. {next piece of work}
- **Blocked on:** {nothing / what and who}
- **Handoff note:** {what the next agent should know — where you stopped, any trap}
- **Last checkpoint:** {ISO timestamp}
