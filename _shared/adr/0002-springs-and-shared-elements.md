# 0002 — Springs and shared elements, not tweens and not a third-party animation library

- **Status:** accepted
- **Date:** 2026-10-26
- **Deciders:** the user (asked for spring/overshoot motion)
- **Supersedes:** the 2026-10-21 tween-based motion pass inside `app-shell`

## Context

The app had no motion code until 2026-10-21, when a first pass added fixed 100/200/300 ms tweens (fade-through tabs, shared-axis pushes, an animated bottom bar). The user reports the result still feels rough and asks for "hyperbolic" motion: spring physics with overshoot.

Constraints that shaped the decision:

- Local, offline prototype; UI efficiency is priority #1; no dependency churn.
- Compose BOM `2026.09.00` pins `androidx.compose.animation 1.12.1`, `material3 1.4.0`, `foundation 1.12.1`, `navigation-compose 2.10.2`, Kotlin `2.3.21`.
- The project is now MIT-licensed; only permissive dependencies are acceptable.
- minSdk 24; the client's device is unknown.

## Options

1. **Material3 `MotionScheme.expressive()`** — the intended API, but it first appears in `material3 1.5.0-beta02`. Adopting it means overriding the BOM with a beta on a stability-first prototype.
2. **Compose springs with hand-written tokens** — encode the published M3 Expressive spring values (damping/stiffness) in one file, use `SharedTransitionLayout`, `animateItem`, and navigation transitions.
3. **A third-party animation library (Lottie, Konfetti, Reorderable, MotionLayout)** — evaluated individually; see `modules/animations/animations.md#Excluded`.
4. **Keep tweens, shorten them** — cheapest, but preserves the core defect: a tween restarts on interruption instead of carrying velocity.

## Decision

Option 2. Seven `spring()` tokens (four spatial incl. one "pop", three effects), stable shared-element APIs, `Modifier.animateItem()` for lists, and `NavHost` enter/pop transitions with predictive back. No new dependency, no version bump.

## Consequences

- `motion/Motion.kt` is the single motion vocabulary; feature screens never define durations.
- `app-shell`'s tween pass (`theme/Motion.kt`, `AnimatedVisibility` bar) is replaced, so `app-shell` is `build: needs-rework` until the animations module lands.
- When `material3 1.5.0` ships stable, only `Motion.kt` changes — the tokens map 1:1 onto `MotionScheme`.
- Overshoot is bounded by policy (≤13% small, ≤2% large, 0% text) so "playful" never becomes "broken".
- Predictive-back preview is Android 13+; older devices get the same transition without the preview.
- Revisit only if a real animation asset (e.g. an illustrated empty state) arrives with a designer — then re-evaluate Lottie after its CVE is fixed.
