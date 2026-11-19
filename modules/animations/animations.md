---
module: animations
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["app-theme"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["motion-system"]  # what this module exposes to others (use _shared/glossary.md terms)
build: in-progress   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-11-19
---

# Animations

A Hyprland-derived motion system for every screen: shared specs, navigational transitions, shared elements, and the small interactions that make taps feel physical.
It replaces the 2026-10-21 generic tween pass with Hyprland's own leaves, curves and springs, so the app's motion matches the desktop the teacher already knows instead of feeling hard-cut or linear.

## # In Use
- **`androidx.compose.animation` 1.12.1** (BOM-managed, Apache-2.0) — `tween()` / `spring()` / `keyframes()` / `snap()` specs, `AnimatedContent`, `AnimatedVisibility`, `SharedTransitionLayout`, `sharedElement`/`sharedBounds`; already on the classpath.
- **`androidx.compose.foundation` 1.12.1** (BOM-managed, Apache-2.0) — `Modifier.animateItem()` for list reordering and `animateContentSize` for small containers.
- **`androidx.compose.material3` 1.4.0** (BOM-managed, Apache-2.0) — component-internal motion only (sheets, ripples, navigation bar). App specs live in `HyprMotion.kt`; `MotionScheme` is not consumed as an API.
- **`navigation-compose` 2.10.2** (Apache-2.0) — `enterTransition` / `popEnterTransition` / `popExitTransition` on `NavHost`, plus predictive-back handoff.
- **`androidx.compose.ui:ui-test-junit4` 1.12.1** (test only, Apache-2.0) — `mainClock` control to assert transitions settle.

## # Excluded
- **Material3 `MotionScheme`** — the API exists only in `material3 1.5.0-beta02`; the BOM pins 1.4.0, so using it means overriding the BOM with a beta. Motion values are hand-rolled in `HyprMotion.kt` instead, no beta.
- **Lottie (`com.airbnb.android:lottie-compose`)** — Apache-2.0, but 6.7.1 and earlier carry **CVE-2026-95179** (DoS via a crafted JSON, CVSS 7.5, published 2026-10-15) with no fixed release at the time of research; it also needs an After Effects asset pipeline for a prototype with no designer and no animation assets.
- **Konfetti (`nl.dionsegijn:konfetti-compose`)** — ISC-licensed and MIT-compatible, but it adds a particle engine for one celebration moment; the completion check is drawn in Compose instead.
- **Reorderable / drag-to-reorder libraries** — no screen in this app reorders items manually; `animateItem()` covers the automatic reordering the lists do.
- **MotionLayout / `constraintlayout-compose`** — a second animation paradigm from the Views era; duplicates what the `HyprMotion` specs and shared elements already do, and would split the design system.
- **Generic 100/200/300 ms tween durations (the old `theme/Motion.kt`)** — arbitrary durations and easings with no relationship to the desktop's motion; the app now uses Hyprland's published leaf specs in `HyprMotion.kt` instead.
- **`DeferredAnimatedContent` / `DeferredAnimatedVisibility`** — new in 1.12 but still behind `ExperimentalDeferredTransitionApi` until 1.13; predictive back is delivered through `NavHost` transitions instead.
- **Manual `PredictiveBackHandler`** — navigation-compose 2.10.2 already draws the system preview and hands off to the pop transition; a hand-rolled handler would fight it.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| Motion runtime | `androidx.compose.animation` | `1.12.1` (BOM `2026.09.00`) | tween/spring specs, transitions and stable shared elements without a version bump |
| Motion specs | hand-rolled `motion/HyprMotion.kt` | Hyprland leaf timings, curves and springs (see below) | `MotionScheme` is beta-only in material3 1.5.0-beta02; Hyprland's own values are the source |
| Design system | `androidx.compose.material3` | `1.4.0` (BOM) | component motion stays consistent with the theme |
| Navigation motion | `navigation-compose` | `2.10.2` (unchanged) | enter/pop transitions + predictive-back integration on the pinned version |
| List motion | `androidx.compose.foundation` `animateItem()` | `1.12.1` (BOM) | stable, key-driven, no extra dependency |
| Predictive back | platform + nav 2.10.2 | API 33+ (`enableOnBackInvokedCallback`) | system gesture preview; older devices fall back to a normal pop |
| Test control | `ui-test-junit4` `mainClock` | `1.12.1` (test only) | deterministic assertions for settle behaviour |

**No dependency is added, removed, or bumped by this module.** Compose 1.12.1, material3 1.4.0, navigation 2.10.2 and Kotlin 2.3.21 stay exactly as pinned.

### Spec vocabulary (the whole motion vocabulary)

`HyprMotion.kt` is the single source of animation specs — no other file may hold a `tween`, `spring`, hardcoded duration or easing. Timings are Hyprland defaults, expressed in deciseconds (speed 1.0 = 100 ms) and scaled by `HyprMotion.SPEED_SCALE`.

| Spec | speed | curve | Use for |
|---|---|---|---|
| `windowsInSpec` | 4.1 | EaseOutQuint | push-in scale (popin 87%) |
| `windowsOutSpec` | 1.49 | Linear | pop-out scale, fast and linear |
| `windowsSpec` | 4.79 | EaseOutQuint | slide-fade movement |
| `fadeInSpec` / `fadeOutSpec` | 1.73 / 1.46 | AlmostLinear | the fade paired with popin |
| `layersInSpec` / `layersOutSpec` | 4.0 / 1.5 | EaseOutQuint / Linear | pure fade (menus, popups, scrims) |
| `workspacesInSpec` / `workspacesOutSpec` | 1.21 / 1.94 | AlmostLinear | peer fade (tab swaps, pop enter) |
| `fadeSpec` | 3.03 | Quick | micro-interaction fade / scale settle |
| `borderSpec` | 5.39 | EaseOutQuint | defined, not yet used |
| `easySpring` / `rubberSpring` | — | critically-damped spring | `easySpring` for shared-element bounds and list placement; `rubberSpring` defined, not yet used |
| `shakeSpec` | 2.8 | keyframes | error shake — the one fixed-duration exception |

Ready-made transitions in `HyprMotion` — `popinIn` / `popinOut` (scale 0.87 + fade), `layerIn` / `layerOut`, `workspaceIn` / `workspaceOut`, `slideFadeIn` / `slideFadeOut` (20% travel) — are what callers actually use; `MotionNav` only picks which leaf applies. `reduce = true` collapses any spec to `snap()` and any transition to `None`.

### Motion rules (binding on every module)

1. **All specs live in `HyprMotion.kt`.** No other file may hold a `tween`, `spring`, `keyframes` or hardcoded duration/easing; call the named spec or transition instead.
2. **Hyprland leaves define the categories.** Window push/pop scales with fade (popin 87%); peer/tab swaps only fade (workspaces); popups and scrims fade (layers); movement slide-fades 20%. Do not mix them per screen.
3. **`keyframes` is the one fixed-duration exception** — the error shake. A celebration check draw is done in Compose, not as a wobbling spring. `Overshoot` exists in `HyprEasing` but is unused: no bounce by default.
4. **Motion explains a change.** It shows where a thing came from or went. No idle loops, no entrance animation on every recomposition, no animation that delays the teacher's next tap.
5. **Reduce motion is total.** When `LocalReduceMotion` is true every spec becomes `snap()` and every transition `None` — no one-frame intermediate scale, no delayed content.

## File & folder layout
```
app/src/main/java/com/example/helper/motion/
├─ HyprMotion.kt      HyprEasing curves, all specs and ready-made transitions, ProvideHyprMotion / LocalReduceMotion
├─ MotionNav.kt       picks the Hyprland leaf for each AppNavHost enter/exit/pop transition
├─ SharedMotion.kt    SharedTransitionLayout host and bounds transform
├─ MotionEffects.kt   pressScale, popOnChange, shake modifiers
├─ MotionKeys.kt      stable shared-element keys (student, class, homework, attachment)
├─ ScreenTakeover.kt  full-screen takeover wrapper
├─ MotionDiag.kt      debug-only back/transition tracing (BuildConfig.DEBUG)
└─ Throttle.kt        tap throttling helper
```
Relative to the app module's main source set, under the project's root namespace. Example recipes: `modules/animations/examples.md`.

## Setup sequence
1. Create `motion/HyprMotion.kt` as the single spec source: `HyprEasing` curves, `ms()`/`SPEED_SCALE` clock, the tween/spring specs and the ready-made transitions. `ProvideHyprMotion` publishes `LocalReduceMotion` from `ANIMATOR_DURATION_SCALE`, observed live via a `ContentObserver`; enable `buildConfig = true` only for the debug `MotionDiag` tracer.
2. `MotionNav.kt` takes over all four `NavHost` transition lambdas, including the predictive ones — the predictive lambdas must delegate to the same `popEnter`/`popExit` helpers or Android 16 back uses Navigation's `scaleOut(0.7f)` default instead of HyprMotion's 0.87 + fade.
3. Add `MotionKeys.kt` (keys are the existing Room ids — `student:42`, `class:7`, `homework:19`) and `SharedMotion.kt` (`SharedTransitionLayout` around `AppNavHost`, `sharedElement` for the student photo, `sharedBounds` for the student name).
4. Set `android:enableOnBackInvokedCallback="true"` on `<application>` and give `NavHost` explicit `popEnterTransition` / `popExitTransition`; confirm the predictive-back preview follows the finger before commit.
5. Apply the micro-interactions in `examples.md` in this order: attendance status tap → counters → chips → list items → bar hide/show. One chunk per checkpoint, gate green after each.
6. Run the QA script in `examples.md` on a real device (60 Hz and 120 Hz), with animator scale set to 0, 0.5, 1 and 2, and in RTL.

## Why this shape
The app's first motion pass used generic 100/200/300 ms tweens with no relationship to the teacher's desktop, so it felt mechanical. `HyprMotion` reproduces Hyprland's own leaf timings, curves and springs in one file, giving the app the same motion language as the window manager it replaces.

The obvious shortcut was `MaterialTheme.motionScheme` (expressive), but it is only in `material3 1.5.0-beta02`. Taking a beta means overriding a BOM that was chosen deliberately for stability, on a prototype whose whole case is "it just works". Encoding Hyprland's published values in one file gets the intended feel with zero version risk.

The three changes that actually move the needle are, in order: shared elements on the student row → student page (roster is the screen the teacher returns to most), predictive back on every push (the gesture already exists; the app just ignores it), and Hyprland navigation (removes the hard-cut feeling on tab switches and pushes). Micro-interactions are polished afterwards because they are cheap and cannot rescue a janky navigation layer.

Everything stays on the classpath the project already has. Third-party animation libraries were evaluated and rejected on evidence, not taste: Lottie's current release has an unfixed high-severity CVE, Konfetti is off-brief, and the reorderable/MotionLayout libraries solve problems this app does not have. That keeps the APK small, the licence surface MIT-compatible, and the offline constraint intact.

## Connections
- **Consumes:** `app-theme` — the Material 3 theme (colour/type/`ShellDimens`) applied at the root, which motion runs under.
- **Produces:** `motion-system` — `HyprMotion` specs and transitions, `ProvideHyprMotion` / `LocalReduceMotion`, `MotionNav` transition choices for `AppNavHost`, `SharedMotionHost` + `MotionKeys`, and the `MotionEffects` modifiers. Feature screens call the modifiers; they never define their own specs.
- **Talks to via:** in-process composables and `Modifier` extensions. `app-shell` owns the `NavHost` and the shared-transition host; feature screens only tag their content with `MotionKeys` and call `MotionEffects`. No feature imports another feature's motion code.

## Open questions
- None. Predictive back is API 33+; on API 24–32 the same screen transition runs without the system preview, which is an accepted degradation, not an open decision.

## Build notes for the implementing agent
- **Order matters:** specs → nav transitions → shared elements → micro-interactions. Do not start with the confetti-level detail; a smooth push is worth more than ten pops.
- **Do not bump anything.** Compose BOM `2026.09.00`, material3 `1.4.0`, nav `2.10.2`, Kotlin `2.3.21` stay. If a motion API appears missing, it is a wrong import, not a version problem.
- **Predictive and normal pop must share one spec.** Both predictive lambdas delegate to `MotionNav.popEnter`/`popExit`; never leave a transition at the library default.
- **Modifier order for shared elements:** size/padding first (left), then `sharedElement`, then `clip`. Getting this backwards makes the image jump at the end of the transition.
- **Keys must be stable and unique** (`student:$id`), never list indexes. `animateItem()` and `sharedElement` both rely on them.
- **Fades stay eased, never bouncy.** Fades use the AlmostLinear/Quick eased tweens in `HyprMotion`; an overshoot easing on alpha clamps and reads as a flicker.
- **Do not animate first composition.** Entrance animations fire once, from a `MutableTransitionState` starting `false`; a plain `AnimatedVisibility(true)` animates every time the screen recomposes.
- **Reduce motion:** Compose respects the system animator scale; `ProvideHyprMotion` observes it live and the 0-scale path must be instant, not a 1-frame stutter with delayed content.
- **Layout cost:** animate `graphicsLayer` (scale/alpha/translation) or `Modifier.offset { }`; never animate padding/height on lists of 40–60 rows.
- **120 Hz budget is 8.3 ms/frame.** One `SharedTransitionLayout` at the root, no nested shared-element hosts.
- **Text never overshoots.** Use `sharedBounds` for text, not `sharedElement`.
- **RTL is free only if you use `slideIntoContainer`/`slideOutOfContainer`** — manual `IntOffset` sliding does not mirror.

## Build progress
- **Owner:** `opencode`. IN-PROGRESS 2026-10-27T01:37:28+05:30.
- **Coded:**
  - `motion/HyprMotion.kt` — `HyprEasing` curves, global clock (`SPEED_SCALE`/`ms()`), tween/spring specs, ready-made transitions, `ProvideHyprMotion` / `LocalReduceMotion` (live `ContentObserver`), `hyprAnimateItem`.
  - `motion/MotionKeys.kt` — stable keys.
  - `motion/SharedMotion.kt` — `SharedTransitionLayout` host.
  - `motion/MotionNav.kt` — workspace fades for tabs/pop-enter, popin 87% + fade for push/pop-exit; all four lambdas including predictive.
  - `motion/MotionEffects.kt` — `pressScale`, `popOnChange`, `shake`.
  - `AppNavHost` uses `MotionNav` + `SharedMotionHost`; `AndroidManifest.xml` sets `android:enableOnBackInvokedCallback="true"`.
  - Micro-interactions: attendance counters and student rows/icons pop with the spec vocabulary; tag/template chips pop on selection/state; long-press stays on chip modifier.
  - Student-card open is a deliberate 0.2 s exception to the "one leaf per category" rule: `MotionNav.enter` picks `HyprMotion.quickPopinIn` for `Routes.STUDENT`, and `rememberBoundsTransform` uses the same 0.2 s spec so the avatar/name finish together. All other routes keep their Hyprland timings.
- **Remaining** (next first):
  1. Shared elements (student photo/name) if needed, or skip if blocked by scope.
  2. QA per script (animator scale 0/0.5/2, RTL, rapid taps, predictive back).
- **Blocked on:** none.
- **Last checkpoint:** 2026-11-19T09:41:00+05:30 — predictive pop transitions now delegate to
  `MotionNav.popEnter`/`popExit` instead of Navigation-Compose's `scaleOut(0.7f)` default; debug-only
  `MotionDiag` tracing added as the regression guard. Android 16 device capture confirms
  `PREDICTIVE_POP_EXIT ... spec=HyprMotion`; compile, assemble and unit tests green. Details in
  `BUGHUNT.md`.
