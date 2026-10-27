---
module: animations
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["app-theme"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["motion-system"]  # what this module exposes to others (use _shared/glossary.md terms)
build: in-progress   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-26
---

# Animations

A spring-physics motion system for every screen: shared tokens, navigational transitions, shared elements, and the small interactions that make taps feel physical.
It replaces the 2026-10-21 tween pass with M3-expressive spring tokens, so motion is interruptible, directional, and slightly overshooting instead of hard-cut or linear.

## # In Use
- **`androidx.compose.animation` 1.12.1** (BOM-managed, Apache-2.0) — `spring()`, `AnimatedContent`, `AnimatedVisibility`, `SharedTransitionLayout`, `sharedElement`/`sharedBounds`; already on the classpath.
- **`androidx.compose.foundation` 1.12.1** (BOM-managed, Apache-2.0) — `Modifier.animateItem()` for list reordering and `animateContentSize` for small containers.
- **`androidx.compose.material3` 1.4.0** (BOM-managed, Apache-2.0) — component-internal motion only (sheets, ripples, navigation bar). Its spring tokens are copied into `Motion.kt`, not consumed as an API.
- **`navigation-compose` 2.10.2** (Apache-2.0) — `enterTransition` / `popEnterTransition` / `popExitTransition` on `NavHost`, plus predictive-back handoff.
- **`androidx.compose.ui:ui-test-junit4` 1.12.1** (test only, Apache-2.0) — `mainClock` control to assert transitions settle.

## # Excluded
- **Material3 `MotionScheme`** — the API exists only in `material3 1.5.0-beta02`; the BOM pins 1.4.0, so using it means overriding the BOM with a beta. The six M3-expressive spring values are encoded in `Motion.kt` instead, identical behaviour, no beta.
- **Lottie (`com.airbnb.android:lottie-compose`)** — Apache-2.0, but 6.7.1 and earlier carry **CVE-2026-95179** (DoS via a crafted JSON, CVSS 7.5, published 2026-10-15) with no fixed release at the time of research; it also needs an After Effects asset pipeline for a prototype with no designer and no animation assets.
- **Konfetti (`nl.dionsegijn:konfetti-compose`)** — ISC-licensed and MIT-compatible, but it adds a particle engine for one celebration moment; the completion check is drawn in Compose instead.
- **Reorderable / drag-to-reorder libraries** — no screen in this app reorders items manually; `animateItem()` covers the automatic reordering the lists do.
- **MotionLayout / `constraintlayout-compose`** — a second animation paradigm from the Views era; duplicates what springs and shared elements already do, and would split the design system.
- **Tween/easing navigation (the current `theme/Motion.kt`, 100/200/300 ms)** — duration-based specs cannot preserve velocity when interrupted, so a second tap mid-transition stutters; springs are interruptible by design.
- **`DeferredAnimatedContent` / `DeferredAnimatedVisibility`** — new in 1.12 but still behind `ExperimentalDeferredTransitionApi` until 1.13; predictive back is delivered through `NavHost` transitions instead.
- **Manual `PredictiveBackHandler`** — navigation-compose 2.10.2 already draws the system preview and hands off to the pop transition; a hand-rolled handler would fight it.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| Motion runtime | `androidx.compose.animation` | `1.12.1` (BOM `2026.09.00`) | springs, transitions and stable shared elements without a version bump |
| Spring tokens | hand-rolled `motion/Motion.kt` | M3-expressive token values (see below) | `MotionScheme` is beta-only in material3 1.5.0-beta02; the values are the same |
| Design system | `androidx.compose.material3` | `1.4.0` (BOM) | component motion stays consistent with the theme |
| Navigation motion | `navigation-compose` | `2.10.2` (unchanged) | enter/pop transitions + predictive-back integration on the pinned version |
| List motion | `androidx.compose.foundation` `animateItem()` | `1.12.1` (BOM) | stable, key-driven, no extra dependency |
| Predictive back | platform + nav 2.10.2 | API 33+ (`enableOnBackInvokedCallback`) | system gesture preview; older devices fall back to a normal pop |
| Test control | `ui-test-junit4` `mainClock` | `1.12.1` (test only) | deterministic assertions for settle behaviour |

**No dependency is added, removed, or bumped by this module.** Compose 1.12.1, material3 1.4.0, navigation 2.10.2 and Kotlin 2.3.21 stay exactly as pinned.

### Spring tokens (the whole motion vocabulary)

| Token | dampingRatio | stiffness | Overshoot | ≈ settle (2%) | Use for |
|---|---|---|---|---|---|
| `Motion.Spatial.Fast` | `0.6` | `800` | 9.5% | 240 ms | pushes, list placement, icon pops |
| `Motion.Spatial.Default` | `0.8` | `380` | 1.5% | 260 ms | tab/content swaps, sheet content, cards |
| `Motion.Spatial.Slow` | `0.8` | `200` | 1.5% | 355 ms | shared-element bounds, bar hide/show |
| `Motion.Spatial.Pop` | `0.55` | `900` | 12.6% | 240 ms | small taps only: chips, counters, checkmarks |
| `Motion.Effects.Fast` | `1.0` | `3800` | 0% | 65 ms | colour, alpha on tap feedback |
| `Motion.Effects.Default` | `1.0` | `1600` | 0% | 100 ms | scrims, crossfades, row backgrounds |
| `Motion.Effects.Slow` | `1.0` | `800` | 0% | 140 ms | large area fades, empty-state fade |

Values are the Material 3 Expressive spring tokens (`md.sys.motion.spring.*`), reproduced as Compose `spring()` specs. Overshoot = `exp(-πζ/√(1-ζ²))`; settle ≈ `4/(ζ·√stiffness)` with mass 1. The M3 web-equivalent durations (150/200/300 ms for effects) measure a longer, imperceptible tail; the spring reaches the same visual result sooner. Do not invent a token per screen — these seven cover the app.

### Motion rules (binding on every module)

1. **Spatial springs move things; effects springs fade things.** Alpha, colour and elevation always use damping `1.0`. A bouncy alpha spring overshoots past 1.0 and clamps, which reads as a flicker.
2. **Overshoot is budgeted.** ≤ 13% on elements under ~64 dp, ≤ 2% on anything larger than a card, **0% on text**. The pop is deliberate, never accidental.
3. **Interruptible beats pretty.** Every transition uses a spring, so a second tap mid-flight continues the velocity instead of restarting. Fixed-duration (`keyframes`) is allowed only for the error shake and the completion check draw.
4. **Motion explains a change.** It shows where a thing came from or went. No idle loops, no entrance animation on every recomposition, no animation that delays the teacher's next tap.
5. **One spring per element.** A row does not simultaneously scale, slide and fade on different curves; pick the dominant movement and let opacity follow it.

## File & folder layout
```
app/src/main/java/com/example/helper/motion/
├─ Motion.kt          the seven spring tokens + reduced-motion helpers (replaces theme/Motion.kt)
├─ MotionNav.kt       tab (fade-through) and push/pop (shared-axis) specs for AppNavHost
├─ SharedMotion.kt    SharedTransitionLayout host, shared keys, bounds transform
├─ MotionEffects.kt   pressScale, popOnChange, shake, checkmark draw, pulse modifiers
└─ MotionKeys.kt      stable shared-element keys (student, class, homework, attachment)
```
Relative to the app module's main source set, under the project's root namespace. Example recipes: `modules/animations/examples.md`.

## Setup sequence
1. Create `motion/Motion.kt` with the seven tokens above and a `LocalMotionEnabled` check derived from the system animator scale; no Gradle change (Compose animation and foundation are already pulled in by `material3` and the BOM).
2. Replace every `theme/Motion.kt` reference with `motion/Motion`; the old duration/easing enum is deleted once `app-shell` compiles. `MotionNav.kt` takes over the `NavHost` transition lambdas.
3. Add `MotionKeys.kt` (keys are the existing Room ids — `student:42`, `class:7`, `homework:19`) and `SharedMotion.kt` (`SharedTransitionLayout` around `AppNavHost`, `sharedElement` for the student photo, `sharedBounds` for the student name).
4. Set `android:enableOnBackInvokedCallback="true"` on `<application>` and give `NavHost` explicit `popEnterTransition` / `popExitTransition`; confirm the predictive-back preview follows the finger before commit.
5. Apply the micro-interactions in `examples.md` in this order: attendance status tap → counters → chips → list items → bar hide/show. One chunk per checkpoint, gate green after each.
6. Run the QA script in `examples.md` on a real device (60 Hz and 120 Hz), with animator scale set to 0, 0.5, 1 and 2, and in RTL.

## Why this shape
The app's first motion pass used fixed 100/200/300 ms tweens. That is why it still feels mechanical: a tween restarts from zero when interrupted, so fast tapping produces visible stutter, and linear timing has no weight. Springs are the native Compose answer — they preserve velocity, need no duration, and are the basis of Material 3 Expressive motion.

The obvious shortcut was `MaterialTheme.motionScheme` (expressive), but it is only in `material3 1.5.0-beta02`. Taking a beta means overriding a BOM that was chosen deliberately for stability, on a prototype whose whole case is "it just works". Copying the six published token values into one file gets the same feel with zero version risk; if material3 1.5 goes stable, `Motion.kt` is the only file that changes.

The three changes that actually move the needle are, in order: shared elements on the student row → student page (roster is the screen the teacher returns to most), predictive back on every push (the gesture already exists; the app just ignores it), and spring navigation (removes the hard-cut feeling on tab switches and pushes). Micro-interactions are polished afterwards because they are cheap and cannot rescue a janky navigation layer.

Everything stays on the classpath the project already has. Third-party animation libraries were evaluated and rejected on evidence, not taste: Lottie's current release has an unfixed high-severity CVE, Konfetti is off-brief, and the reorderable/MotionLayout libraries solve problems this app does not have. That keeps the APK small, the licence surface MIT-compatible, and the offline constraint intact.

## Connections
- **Consumes:** `app-theme` — the Material 3 theme (colour/type/`ShellDimens`) applied at the root, which motion runs under; `motion-system` replaces the duration tokens inside `theme/Motion.kt`.
- **Produces:** `motion-system` — the `Motion` token object, `MotionNav` transition specs for `AppNavHost`, `SharedMotionHost` + `MotionKeys`, and the `MotionEffects` modifiers. Feature screens call the modifiers; they never define their own durations.
- **Talks to via:** in-process composables and `Modifier` extensions. `app-shell` owns the `NavHost` and the shared-transition host; feature screens only tag their content with `MotionKeys` and call `MotionEffects`. No feature imports another feature's motion code.

## Open questions
- None. Predictive back is API 33+; on API 24–32 the same screen transition runs without the system preview, which is an accepted degradation, not an open decision.

## Build notes for the implementing agent
- **Order matters:** tokens → nav transitions → shared elements → micro-interactions. Do not start with the confetti-level detail; a smooth push is worth more than ten pops.
- **Do not bump anything.** Compose BOM `2026.09.00`, material3 `1.4.0`, nav `2.10.2`, Kotlin `2.3.21` stay. If a spring API appears missing, it is a wrong import, not a version problem.
- **Modifier order for shared elements:** size/padding first (left), then `sharedElement`, then `clip`. Getting this backwards makes the image jump at the end of the transition.
- **Keys must be stable and unique** (`student:$id`), never list indexes. `animateItem()` and `sharedElement` both rely on them.
- **No alpha bounce.** If a fading element flickers, an effects spring was swapped for a spatial one.
- **Do not animate first composition.** Entrance animations fire once, from a `MutableTransitionState` starting `false`; a plain `AnimatedVisibility(true)` animates every time the screen recomposes.
- **Reduce motion:** Compose respects the system animator scale; verify it instead of adding a custom switch. The 0-scale path must be instant, not a 1-frame stutter with delayed content.
- **Layout cost:** animate `graphicsLayer` (scale/alpha/translation) or `Modifier.offset { }`; never animate padding/height on lists of 40–60 rows.
- **120 Hz budget is 8.3 ms/frame.** One `SharedTransitionLayout` at the root, no nested shared-element hosts.
- **Text never overshoots.** Use `sharedBounds` for text, not `sharedElement`.
- **RTL is free only if you use `slideIntoContainer`/`slideOutOfContainer`** — manual `IntOffset` sliding does not mirror.

## Build progress
- **Owner:** `opencode`. IN-PROGRESS 2026-10-27T01:37:28+05:30.
- **Coded:**
  - `motion/Motion.kt` — seven spring tokens (spatial Fast/Default/Slow/Pop + offsets/sizes/ints, effects Fast/Default/Slow) + `LocalMotionEnabled`/`ProvideMotion`.
  - `motion/MotionKeys.kt` — stable keys.
  - `motion/SharedMotion.kt` — `SharedTransitionLayout` host.
  - `motion/MotionNav.kt` — fade-through tabs, shared-axis push/pop.
  - `motion/MotionEffects.kt` — `pressScale`, `popOnChange`, `shake`.
  - `AppNavHost` uses `MotionNav` + `SharedMotionHost`; `AppScaffold` bottom bar uses springs; `AndroidManifest.xml` sets `android:enableOnBackInvokedCallback="true"`.
  - Micro-interactions: attendance counters roll with springs, student rows/icons pop lightly; tag/template chips pop on selection/state; long-press stays on chip modifier.
- **Remaining** (next first):
  1. Shared elements (student photo/name) if needed, or skip if blocked by scope.
  2. QA per script (animator scale 0/0.5/2, RTL, rapid taps, predictive back).
- **Blocked on:** none.
- **Last checkpoint:** 2026-10-27T02:25:00+05:30




