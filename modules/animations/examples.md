# Animations — worked examples

Companion to `animations.md`. Each recipe is a decision the implementing agent can apply directly: what triggers it, what moves, which token, what must **not** move, and how to prove it landed. No code — the card's `File & folder layout` and `Setup sequence` are the build instruction.

Read `animations.md` first for the seven spring tokens. This file only spends them.

---

## Before / after, in one page

| Moment | Today (tween pass) | Target (spring system) | Why it feels better |
|---|---|---|---|
| Tab switch | 200 ms fade | fade-through: outgoing scale 0.92 + fade out (fast spatial), incoming fade in | directionless crossfade becomes a physical swap with weight |
| Push to a student | slide + fade, 300 ms, restarts on interruption | shared-axis slide with `Spatial.Fast`, velocity preserved | double-tapping a row no longer stutters |
| Back | same slide in reverse | predictive-back preview + mirrored pop, scale 0.92 | the gesture previews the destination instead of guessing |
| Bottom bar on nested screens | slide + expand + fade, fixed 300 ms | `Spatial.Fast` slide/shrink, content padding follows | bar and content settle together, no end-of-animation jump |
| Attendance tap | colour snap, counter jumps | row colour effects spring, icon pop 0.96→1.0, counter rolls | a 60-row list feels responsive without noise |
| Student photo → page | hard cut | shared element, key `student:$id` | the teacher keeps visual track of who they opened |
| Feedback list edit | items jump | `animateItem()` placement spring | rows move instead of teleporting |

---

## Spring maths, so nobody guesses

Overshoot = `exp(-πζ/√(1-ζ²))`; settle ≈ `4/(ζ·√stiffness)` (Compose mass = 1).

| dampingRatio | Overshoot | Reads as |
|---|---|---|
| 1.0 | 0% | calm, no bounce (effects only) |
| 0.9 | 0.15% | barely perceptible settle |
| 0.8 | 1.5% | confident, almost no bounce (default spatial) |
| 0.6 | 9.5% | springy, clearly alive (fast spatial) |
| 0.55 | 12.6% | playful pop (small elements only) |
| 0.5 | 16.3% | too much for full screens |
| 0.35 | 30.9% | cartoonish; not this app |

**Rule of thumb:** if the element is smaller than a thumb, it may overshoot; if it contains body text or fills the screen, it must not.

---

## Recipes

### 1. Tab switch — fade-through, not crossfade
- **Trigger:** tapping a bottom-bar tab.
- **Moves:** outgoing destination scales to 0.92 while fading out; incoming fades in at full scale.
- **Spec:** scale `Spatial.Fast`; alpha `Effects.Fast`; no slide.
- **Must not:** slide horizontally (tabs are siblings, not a stack), or animate the bottom bar itself.
- **Pass:** tapping Today → Students → Today quickly shows one continuous settle, never two half-faded screens.

### 2. Push — shared axis X
- **Trigger:** class row → student list, student row → student page, Today row → attendance.
- **Moves:** incoming screen enters from 30% of its width; outgoing leaves by 30% the other way; both fade.
- **Spec:** translation `Spatial.Fast`; alpha `Effects.Fast`; use `slideIntoContainer` / `slideOutOfContainer` so RTL mirrors automatically.
- **Must not:** animate the top app bar separately from the content — they are one surface.
- **Pass:** a rapid double-tap on a row never shows a flash of the previous screen; back reverses the exact path.

### 3. Predictive back
- **Trigger:** system back gesture on any pushed screen.
- **Moves:** the screen follows the finger (system preview), then the pop transition completes it; `popExitTransition` scales to 0.92 and fades.
- **Spec:** `Spatial.Default` for the pop exit; alpha `Effects.Default`. Manifest: `android:enableOnBackInvokedCallback="true"`.
- **Must not:** intercept back with a custom handler; never make the preview disagree with the final animation.
- **Pass:** cancel a half-swipe — screen returns with no jump; commit a half-swipe — no flash at handoff.

### 4. Bottom bar hide/show on nested screens
- **Trigger:** entering/leaving a route that is not a tab root.
- **Moves:** bar slides down by its height, shrinks slightly, fades; the content's bottom padding animates with it.
- **Spec:** slide/shrink `Spatial.Fast`; alpha `Effects.Default`.
- **Must not:** change content height after the animation ends; the padding must be part of the same transition.
- **Pass:** watch the last 100 ms — no 1–2 dp content jump when the bar finishes.

### 5. Attendance status tap (the hot path)
- **Trigger:** tapping a student row to toggle Present/Absent.
- **Moves:** row background crossfades; status icon scales 0.96 → 1.0 with a small overshoot; the absent counter rolls to its new value.
- **Spec:** background `Effects.Fast` (never spatial); icon `Spatial.Pop`; counter `Spatial.Fast`.
- **Must not:** animate the whole row's size, or re-layout the list on each tap. Keep the row height fixed.
- **Pass:** tapping 20 rows as fast as possible never drops a tap, and the counter stays legible mid-roll.

### 6. Counter changes (Present / Absent / total)
- **Trigger:** the number behind a counter changes.
- **Moves:** old digit slides up and out, new digit slides in from below; container scales 1.0 → 1.04 → 1.0 once.
- **Spec:** digit `Spatial.Fast`; container `Spatial.Pop`; fixed-width digits so the row does not shift.
- **Must not:** animate on first composition — only on a real change.
- **Pass:** first open of the attendance screen shows the numbers already settled.

### 7. Save attendance — completion beat
- **Trigger:** the last student is marked and the teacher saves.
- **Moves:** a checkmark draws itself (200 ms path trim), a ring pulses 1.0 → 1.06 → 1.0 once, then the sheet dismisses.
- **Spec:** check draw is fixed-duration `keyframes` (allowed exception); pulse `Spatial.Pop`; dismiss `Spatial.Default`.
- **Must not:** block the next action, add confetti, or run longer than ~450 ms total.
- **Pass:** the teacher can start the next class before the pulse ends; with animator scale 0 the save is instant.

### 8. FAB → student picker sheet
- **Trigger:** "Give feedback" FAB.
- **Moves:** M3 `ModalBottomSheet` already animates with its own spring; the scrim fades.
- **Spec:** leave the sheet's internal motion alone; scrim `Effects.Default`.
- **Must not:** wrap the sheet in a second `AnimatedVisibility` or custom slide — double motion looks broken.
- **Pass:** drag the sheet down and release mid-way; it continues with the gesture velocity.

### 9. Student row → student page (the hero)
- **Trigger:** opening a student.
- **Moves:** the student photo flies and resizes from the row to the page header; the name's bounds expand from row typography to header typography; the rest of the page fades in.
- **Spec:** photo `sharedElement` keyed `student:$id`; name `sharedBounds`; bounds transform `Spatial.Slow`; overlay clip follows the photo's shape (circle → rounded rect).
- **Must not:** share the name with `sharedElement` (text reflows), or key by list position.
- **Pass:** pause frame-by-frame at the landing — no 1 px jump, no double image; back returns the photo to the exact original row.

### 10. Feedback tag chip
- **Trigger:** selecting/deselecting a preset tag.
- **Moves:** chip scales 0.96 → 1.0 with a pop, background crossfades, checkmark fades/scales in.
- **Spec:** scale `Spatial.Pop`; colour and check alpha `Effects.Fast`; fixed chip width so neighbours do not shift.
- **Must not:** animate the chip's width or add a ripple on top of the scale.
- **Pass:** selecting five tags quickly produces five clean pops, no layout shift.

### 11. Feedback timeline add / remove
- **Trigger:** a note is added, edited or deleted.
- **Moves:** other rows slide to their new positions; the new row fades in.
- **Spec:** placement `Spatial.Default`; enter/exit alpha `Effects.Default`, using `Modifier.animateItem()` with the Room id as key.
- **Must not:** animate each row's height; no stagger on more than five rows.
- **Pass:** delete a middle note — the rows below close the gap smoothly and nothing overlaps.

### 12. Homework attachment thumbnail
- **Trigger:** a file is attached.
- **Moves:** thumbnail fades in and scales 0.9 → 1.0; the attachment strip reflows.
- **Spec:** scale `Spatial.Fast`; alpha `Effects.Fast`; strip reflow `animateItem()`.
- **Must not:** animate the whole compose screen or the text field.
- **Pass:** attaching three PDFs in a row keeps the button under the thumb.

### 13. Form error — shake, on purpose
- **Trigger:** saving with a missing required field.
- **Moves:** the field shakes ±4 dp, three oscillations, 280 ms; the error text fades in.
- **Spec:** `keyframes` (duration-based is correct here — a spring would keep wobbling); error alpha `Effects.Fast`.
- **Must not:** shake the whole screen, or repeat the shake while the error is unchanged.
- **Pass:** the shake never moves the field's hit target; TalkBack announces the error once.

### 14. Undo snackbar
- **Trigger:** a destructive action is undone or confirmed.
- **Moves:** snackbar slides up from the bottom and fades in; slides down on dismiss.
- **Spec:** slide `Spatial.Fast`; alpha `Effects.Fast`; respect the FAB inset.
- **Must not:** delay the undo window to "let the animation finish".
- **Pass:** tapping Undo mid-entry works on the first tap.

### 15. Empty states
- **Trigger:** first appearance of "no classes yet", "no feedback yet", "no homework due".
- **Moves:** one fade-and-rise (12 dp) on first composition only.
- **Spec:** rise `Spatial.Default`; alpha `Effects.Default`.
- **Must not:** loop, shimmer, or re-animate when the user returns to the tab.
- **Pass:** leave and return to the tab — the empty state is already settled.

### 16. Long-press / press feedback
- **Trigger:** finger down on a primary action.
- **Moves:** the surface scales to 0.97 and the tint deepens; release springs back.
- **Spec:** scale `Spatial.Fast` (press down) and `Spatial.Pop` (release); tint `Effects.Fast`.
- **Must not:** add a scale to 60-row list items — it fights scrolling.
- **Pass:** press and drag away — the surface returns without a stuck half-scale.

---

## Anti-patterns (each one seen in real apps)

| Anti-pattern | What it looks like | Fix |
|---|---|---|
| Bouncy alpha | fade flickers or clamps at 1.0 | effects spring, damping `1.0` |
| Animating padding/height on a 60-row list | dropped frames while scrolling | animate scale/translation via `graphicsLayer` |
| Entrance animation on every recomposition | list twitches when data refreshes | `MutableTransitionState(false)` fires once |
| Long full-screen animations | teacher waits before the next tap | `Spatial.Fast` ≤ 240 ms; never > 400 ms |
| Nested shared-element hosts | photo jumps twice | one `SharedTransitionLayout`, one key |
| `sharedElement` on text | letters stretch and wobble | `sharedBounds` for text |
| Custom `IntOffset` slide | wrong direction in RTL | container-relative slide helpers |
| Different tokens on one element | three speeds fighting | one dominant movement per element |
| Animation on scroll-driven rows | jank on cheap devices | static rows; animate only the change |
| Confetti for routine saves | delight becomes noise | completion check only |

---

## QA script (run before calling the module built)

1. **Rapid taps:** switch tabs and push/back 20 times as fast as possible. Nothing stutters, nothing shows two screens at full opacity.
2. **Interrupt:** start a push and tap another row mid-flight. The second transition continues from the current position — no snap to start.
3. **Predictive back:** half-swipe back (preview follows finger), cancel, then commit. No jump at handoff.
4. **Bar handoff:** enter and leave a nested screen; watch the last 100 ms for a content jump.
5. **Shared element:** open a student with and without a photo; the placeholder and the photo both land exactly, and back returns to the row.
6. **Animator scale 0:** Settings → Accessibility → Remove animations. Every action is instant; no content is stuck invisible.
7. **Animator scale 0.5 and 2:** no overlap and no negative durations; content still readable.
8. **RTL:** switch the device to Arabic/Hebrew. Pushes slide from the correct side; icons mirror.
9. **Refresh:** add a feedback note from another screen and return. No entrance animation replays.
10. **120 Hz:** record a screen capture at 120 fps and step through a push — no repeated or dropped frames; no frame over 8.3 ms in the profiler.

## Reference material (verified 2026-10-26)

- Compose animation specs & springs — <https://developer.android.com/develop/ui/compose/animation/customize>
- Compose quick-guide (visibility, size, offset) — <https://developer.android.com/develop/ui/compose/animation/quick-guide>
- Shared elements — <https://developer.android.com/develop/ui/compose/animation/shared-elements>
- Predictive back (setup + nav) — <https://developer.android.com/develop/ui/compose/system/predictive-back-setup>
- M3 motion physics tokens — <https://m3.material.io/styles/motion/overview/specs>
- Compose 1.12 release notes (deferred transitions) — <https://developer.android.com/jetpack/androidx/releases/compose-animation>
- Animated list items — <https://developer.android.com/develop/ui/compose/lists>
- Deferred state reads for animation performance — <https://developer.android.com/develop/ui/compose/performance/bestpractices>
