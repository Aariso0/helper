# MOTION.md — Hyprland motion audit

Phase 0 audit, run before any edits. Baseline `./gradlew assembleDebug --console=plain`
was **successful** (38 tasks up-to-date) on branch `animations` with a clean tree.

## Stack detected

| Item | Value |
|---|---|
| UI toolkit | Jetpack Compose only (no XML layouts, no Views/Fragments) |
| Package | `com.example.helper`; motion code lives in `com.example.helper.motion` |
| minSdk / targetSdk / compileSdk | 24 / 37 / 37 |
| Kotlin | 2.3.21 |
| AGP | 9.5.0 |
| Compose BOM | 2026.09.00 → animation 1.12.1, foundation 1.12.1, material3 1.4.0 |
| Navigation | Navigation-Compose 2.10.2 (`NavHost`, single Activity) |
| Material | Material 3 (`MaterialTheme.colorScheme`) |
| `androidx.dynamicanimation` | **not present** — not needed for Compose |
| Predictive back | `android:enableOnBackInvokedCallback="true"` in the manifest |
| Existing central motion file | `app/src/main/java/com/example/helper/motion/Motion.kt` (M3-expressive spring tokens) |

`animateItem` is available (Foundation 1.12.1), so no `animateItemPlacement` fallback is needed.
No dependency will be added.

## Existing app-controlled animation

| File | Line | What animates | Current spec | Hyprland category |
|---|---|---|---|---|
| `motion/Motion.kt` | 16–56 | Central token object | M3-expressive springs: Spatial Fast 0.6/800, Default 0.8/380, Slow 0.8/200, Pop 0.55/900; Effects NoBouncy 3800/1600/800 | foundation → replace with `HyprMotion` |
| `motion/Motion.kt` | 62–83 | `LocalMotionEnabled` / `ProvideMotion` | Reads `ANIMATOR_DURATION_SCALE`, but **never provided or consumed** | reduce-motion → wire as `LocalReduceMotion` |
| `motion/MotionNav.kt` | 33–79 | NavHost push/pop | Slide ±30% width + fade, `Spatial.FastOffset` / `Effects.Fast` springs | windowsIn/out + workspaces |
| `motion/MotionNav.kt` | 82–87 | Tab → tab switch | incoming `fadeIn(Effects.Fast)`; outgoing `scaleOut(0.92) + fadeOut` | workspaces (peer fade) |
| `motion/MotionEffects.kt` | 27–43 | Press feedback | scale 0.97, spring 0.6/800 down / 0.55/900 up | fade (quick state change) |
| `motion/MotionEffects.kt` | 52–66 | Status indicator pop | snap 0.96 → 1.06 → 1, spring 0.55/900 | windowsIn (popin 87%, no overshoot) |
| `motion/MotionEffects.kt` | 69–91 | Error shake | `keyframes` 280 ms, ±4/3/2 px | rare accent; duration must come from `HyprMotion` |
| `motion/ScreenTakeover.kt` | 31–37 | Homework / announcement full-screen takeover | full-width slide in/out + fade, springs | windowsIn/out (popin) |
| `scaffold/AppScaffold.kt` | 65–73 | Bottom bar show/hide | slide ±full height + expand/shrink from bottom + fade, springs | layersIn/out + windowsMove |
| `attendance/AttendanceScreen.kt` | 318–327 | Live counter roll (`RollingNumber`) | slide ±full height + fade, springs | workspaces (quick fade) |
| `homework/HomeworkList.kt` | 139–143 | `animateItem` on homework rows | placement 0.8/380 spring; fades NoBouncy/3800 | windowsMove + layers |
| `parents/ParentsScreen.kt` | 194–198, 240–244 | `animateItem` on announcement / contact rows | same as above | windowsMove + layers |
| `motion/SharedMotion.kt` | 44–48, 66–70 | Shared element + shared bounds to student detail | `SharedTransitionDefaults.BoundsTransform` (default spring) | windowsMove → `HyprMotion` bounds transform |
| `nav/AppNavHost.kt` | 57–72 | NavHost host | delegates all four transitions to `MotionNav` | covered above |

## Library-owned animation (present, but no public spec injection in Material3 1.4.0)

These animate through Material/platform internals. `BasicAlertDialog` is backed by a Compose
platform `Dialog` with a fixed internal theme; `MotionScheme` and `BasicAlertDialogOverride`
are `internal` in Material3 1.4.0. There is no public API to inject a custom spec, so the app
cannot redirect these without reimplementing the components. They are recorded here rather
than silently ignored.

| File (examples) | Line | What animates | Current spec | Hyprland category |
|---|---|---|---|---|
| `attendance/ui/StatusSheet.kt` | 37–41 | `ModalBottomSheet` + scrim | M3 sheet state + `MotionSchemeKeyTokens.DefaultEffects` | layersIn/out + fadeDim |
| `parents/ui/ParentsScreen.kt` | 428 | `AlertDialog` (confirm delete) | platform dialog window | windowsIn/out |
| `roster/ui/studentlist/StudentListScreen.kt` | 208, 406 | `AlertDialog` ×2 | platform dialog window | windowsIn/out |
| `roster/ui/studentpage/StudentPageScreen.kt` | 379, 467 | `AlertDialog` ×2 | platform dialog window | windowsIn/out |
| `roster/ui/classlist/ClassListScreen.kt` | 192, 350 | `AlertDialog` ×2 | platform dialog window | windowsIn/out |
| `roster/picker/PickerDialog.kt` | 37 | `AlertDialog` (shared picker) | platform dialog window | windowsIn/out |
| `studentfeedback/ui/TagChips.kt` | 112, 150 | `AlertDialog` ×2 | platform dialog window | windowsIn/out |
| `scaffold/SessionMenu.kt` | 66, 137 | `DropdownMenu`, `AlertDialog` | M3 menu/dialog defaults | layersIn/out, windowsIn/out |
| `parents/ui/TemplateChips.kt` | 141, 190 | `AlertDialog` ×2 | platform dialog window | windowsIn/out |
| `parents/ui/ContactLog.kt` | 62, 117 | `AlertDialog`, `DatePickerDialog` | platform dialog window | windowsIn/out |
| `examsresults/ui/ExamDetailScreen.kt` | 416–513, 538, 576, 685 | `AlertDialog` ×8 | platform dialog window | windowsIn/out |
| `examsresults/ui/ExportColumnsScreen.kt` | 222, 313 | `AlertDialog` ×2 | platform dialog window | windowsIn/out |
| `exportbackup/ui/ExportScreen.kt` | 135, 377 | **plain** `Dialog` + `AlertDialog` | platform dialog window | windowsIn/out |
| `roster/ui/import/ImportScreen.kt` | 507, 546, 595 | `ExposedDropdownMenu` ×3 | M3 menu defaults | layersIn/out |
| `examsresults/ui/ExamImportScreen.kt` | 646, 686 | `ExposedDropdownMenu` ×2 | M3 menu defaults | layersIn/out |
| 8 screens with `SnackbarHost` | — | snackbar enter/exit | M3 `Snackbar` defaults | layersIn/out |
| `attendance/AttendanceScreen.kt` | 376 | `DatePickerDialog` | platform dialog window | windowsIn/out |
| `homework/ui/HomeworkEdit.kt` | 351 | `DatePickerDialog` | platform dialog window | windowsIn/out |

The plain `Dialog` (`ExportScreen.kt:135`) uses the same platform window animation as
`AlertDialog`: its content is app-owned but the window transition is not spec-injectable.
It is a non-dismissible progress dialog (`onDismissRequest = {}`), so wrapping its content in
`AnimatedVisibility` would animate on enter only and double up with the platform window
animation. It is left as library-owned, like the dialogs above.

## Missing animation (no animation today)

| File | Line | What should animate | Hyprland category |
|---|---|---|---|
| `roster/ui/studentlist/StudentListScreen.kt` | 183 | student row insert/remove | windowsMove |
| `roster/ui/classlist/ClassListScreen.kt` | 141 | class row insert/remove | windowsMove |
| `attendance/AttendanceScreen.kt` | 256 | student row filter change | windowsMove |
| `studentfeedback/ui/FeedbackScreen.kt` | 122 | feedback row insert | windowsMove |
| `examsresults/ui/ExamListScreen.kt` | 79 | exam row insert/remove | windowsMove |
| `roster/picker/StudentPicker.kt` / `ClassPicker.kt` | 69 / 57 | picker rows | windowsMove |
| `examsresults/ui/MarkEntryScreen.kt` | 169 | mark rows | windowsMove |
| `exportbackup/ui/ExportScreen.kt` | 166 | export rows | windowsMove |
| `TodayScreen`, `HomeworkEdit`, `AnnouncementEdit` | — | loading → content / empty state | fade (quick) |
| selection state everywhere | — | `NavigationBarItem`, pressed/hover alpha, icon swaps | border (540 ms) / fade (quick) |

`AttendanceScreen.kt:238–254` deliberately has no empty-state animation: the item only exists
while empty, so an enter transition would never run. It stays as-is.

## Plan

1. `HyprMotion` foundation + reduce-motion provider → build.
2. NavHost + takeover + bottom bar → build.
3. Effects, lists, shared bounds → build.
4. Cleanup grep + `assembleDebug`, `lint`, unit tests.

## Resolution

All app-controlled specs now live in `motion/HyprMotion.kt`; the old `motion/Motion.kt` token
object is deleted. `SPEED_SCALE` stays at `1.0f` (exact Hyprland timings) — no device feel
evidence to justify `0.85f`. `ProvideHyprMotion` reads `ANIMATOR_DURATION_SCALE` once at the
composition root and maps `0` to `LocalReduceMotion = true`; every transition and effect
honours it via a `reduce` flag (specs collapse to `snap`/`None`).

`hyprAnimateItem()` is a `LazyItemScope` extension (matching `Modifier.animateItem`'s scope),
applied to every keyed list: homework, announcements, contacts, attendance, students, classes,
exams, feedback notes, pickers, mark entry and export summaries. `TodayScreen`, `HomeworkEdit`
and `AnnouncementEdit` loading→content swaps have no `AnimatedContent` and were left alone —
adding one would be new behaviour, not replacing an existing animation.

Library-owned animations (Material3 dialogs, menus, snackbars, `ModalBottomSheet`, platform
dialogs, `DatePickerDialog`) remain unchanged: Material3 1.4.0 keeps `MotionScheme` and
`BasicAlertDialogOverride` internal, so there is no public spec-injection point. No dependency
was added and no library component was reimplemented.

Cleanup grep confirms the only `tween`, `spring`, `keyframes` and `durationMillis` in app
source are inside `HyprMotion.kt`; no other file holds an animation duration.

### Post-audit fix: predictive back (2026-11-19)

The four-transition `NavHost` overload left `predictivePopExitTransition` at the library default
`scaleOut(0.7f)` (≈49% area), so Android 16 gesture and 3-button back collapsed all corners to half
size while the in-app arrow correctly used `HyprMotion.popinOut`'s `scaleOut(0.87f)` + fade. Both
predictive lambdas in `nav/AppNavHost.kt` now delegate to `MotionNav.popEnter`/`popExit`, the same
specs as the non-predictive path. Full evidence, the runtime table and the regression check are in
`BUGHUNT.md`. Debug-only `MotionDiag` tracing remains behind `BuildConfig.DEBUG` as the guard.
