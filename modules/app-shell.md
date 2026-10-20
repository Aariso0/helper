---
module: app-shell
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["data-repositories", "roster-screens", "attendance-screen", "feedback-screen", "quick-note-screen", "homework-screen", "parents-screen", "export-screen"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["share-helpers", "app-theme"]      # what this module exposes to others (use _shared/glossary.md terms)
build: not-started   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-20
---

# App shell

Navigation, theme, and design system enforcing the fewest-taps rules, plus shared share-sheet helpers. Hosts the Today home screen with its attendance nudges and pending-homework counts.

## # In Use
- **Compose BOM** — pins every androidx.compose artifact to a tested combination.
- **Material 3** — NavigationBar, Theme, Snackbar, ripples all built in; the design system base.
- **navigation-compose** — official navigation; all route strings and parsing live only in this module.
- **java.time via core library desugaring** — `LocalDate` "today" and month math on any min SDK.
- **ViewModel + StateFlow (MVVM)** — the README's proposed architecture; ViewModels collect repository `Flow`s.
- **System share sheet (ACTION_SEND)** — the prototype's only sending mechanism, wrapped in `share-helpers`.

## # Excluded
- **kotlinx-datetime (0.8.0)** — on Android it sits on `java.time` anyway (so desugaring is required either way) and it is mid-migration into stdlib `kotlin.time`; an extra API layer for no gain.
- **Manual epoch-day arithmetic** — hand-rolled month/weekday/due-date math is exactly the fragile calendar logic `java.time` exists for.
- **`java.util.Calendar` / `DateFormat`** — pre-Java-8 API, error-prone; superseded by `java.time`.
- **Type-safe routes + kotlinx-serialization plugin** — the official direction, but this design puts every route literal and parse in one file (`nav/`), so a second Kotlin-coupled Gradle plugin (after KSP) buys compile-time safety for one file.
- **Voyager / Decompose / Compose Destinations** — third-party navigation stacks; the official library covers four tabs and a handful of routes.
- **Hilt/Koin ViewModel injection** — manual `AppContainer` per the `core-data` card; no second DI story.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| UI toolkit | Compose BOM | `2026.09.00` | one version set for all androidx.compose artifacts |
| Design system | Material 3 (`material3`, BOM-managed) | `2026.09.00` (BOM) | built-in NavigationBar/Theme/Snackbar, M3 is the Compose default |
| Navigation | `navigation-compose` | `2.10.2` | official, current stable; string routes centralized here |
| State | `lifecycle-viewmodel-compose` + StateFlow | `2.11.0` | MVVM per README; ViewModel collects repository Flows |
| Date & time | `java.time` via core library desugaring | `desugar_jdk_libs 2.1.5` | LocalDate/Period on any min SDK; official AGP feature |
| Sharing | system share sheet (`ACTION_SEND`) | platform (no dependency) | zero-dep, user-initiated send — the prototype's only send path |

## File & folder layout
```
app-shell/
├─ MainActivity.kt       single activity, sets the root composable
├─ theme/                Material 3 color/type/sizing tokens (fewest-taps targets)
├─ scaffold/             bottom NavigationBar: Today · Students · Homework · Parents
├─ nav/                  NavHost, the route table, argument parsing, feature wiring
├─ today/                Today screen + ViewModel (nudges/counts via data-repositories)
└─ share/                share-helpers: ACTION_SEND text/file intent builders
```
Relative to the app module's main source set, under the project's root namespace.

## Setup sequence
1. Add dependencies: `implementation(platform("androidx.compose:compose-bom:2026.09.00"))`, `implementation("androidx.navigation:navigation-compose:2.10.2")`, `implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")`; enable desugaring: `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")` plus `isCoreLibraryDesugaringEnabled = true` in the module's `compileOptions`.
2. Declare the app's single `FileProvider` in the manifest (authority `<applicationId>.fileprovider`, `file_paths` covering the app-internal photo, attachment, and export/PDF directories) — the shared file-sharing infrastructure reused by `homework` (capture + share), `parents` (announcement PDFs), and `export-backup`.
3. Create `theme/` — Material 3 theme with big tap targets and bottom-anchored primary actions (design rules 4 in the README).
4. Create `MainActivity.kt`, `scaffold/` bottom bar, and `nav/` with the four tab routes and the full route table (attendance, student, quick-note, homework, parents, export destinations) even if only some are wired yet.
5. Create `share/` (`share-helpers`, including the file-capable variant taking text + content URIs + mime types) and `today/` (Today screen + ViewModel reading today's classes, attendance status, and due-homework counts through `data-repositories`).
6. Wire feature root composables into `nav/` as each feature module is delivered (see Build notes, wave 2).

## Why this shape
The shell's job is to make every other module's seam obvious, so the navigation contract is deliberately asymmetric: all route strings and argument parsing live in `nav/` (one file set), while feature screens are plain composables taking typed parameters and callbacks — feature modules never depend on navigation, they only get passed `classId`/`studentId`/`date` values. That decision is why string routes beat type-safe routes here despite the official push toward them: the parse surface is a single owned file, and the alternative would add a second Kotlin-coupled Gradle plugin (serialization, next to KSP) for benefit concentrated in exactly that one place. Dates use `java.time` under core library desugaring rather than kotlinx-datetime because kotlinx-datetime itself rides on `java.time` on Android — same desugaring requirement, extra API, plus its own ongoing migration into the stdlib; desugaring is one Gradle flag and makes the unresolved min-SDK question harmless. MVVM with ViewModel + StateFlow is the README's proposal and maps 1:1 onto Room's `Flow`-returning repositories from `core-data`. The Today screen reads only repositories (attendance status for today's date, homework due) — it needs no feature-module internals, which is what lets `app-shell` be drafted third while four feature cards don't exist yet. The bottom bar has exactly the four README tabs; no settings tab (no setup wall), no profile, no menu screen.

## Connections
- **Consumes:** `data-repositories` (Today screen's nudges and counts) from `core-data`; `roster-screens`, `attendance-screen`, `feedback-screen`, `quick-note-screen`, `homework-screen`, `parents-screen`, `export-screen` — each feature's root composable, imported and wired into `nav/` as the owning module is delivered.
- **Produces:** `share-helpers` — ACTION_SEND builders called by attendance/homework/parents screens; `app-theme` — the Material 3 theme applied at the root, which feature screens render under ambiently.
- **Talks to via:** in-process composable imports. `app-shell` is the composition root: it imports every feature's screens, and features import only `share-helpers` back (plus ambient `app-theme`) — a deliberate, documented two-way seam at the shell boundary, handled by the build protocol's message-before-code rule.

## Open questions
- None. (min SDK remains a client question at project level; desugaring removes any dependency this module has on its answer.)

## Build notes for the implementing agent
- Build in two waves: **wave 1** — theme, share-helpers, scaffold, full route table, Today screen (all deliverable with only `core-data`); **wave 2** — import each feature root composable into `nav/` as its module reaches `build: built`/`in-progress`, checkpointing each wiring.
- The route table is this module's contract with itself: add every route string in wave 1, with argument parsing centralized in `nav/` — features must never receive a raw route, only typed values.
- Do not add a settings/profile tab or extra bottom-bar items; four tabs only (README design rule 3).
- Desugaring flag must stay on once dates ship — removing it later would crash every `java.time` call on older devices.

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
