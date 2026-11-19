# Back-animation bug hunt

Branch: `animations` (debug instrumentation added here, behind `BuildConfig.DEBUG`).
Status: **cause proven at runtime — path B, fixed; the "about 2 seconds" perception remains
unexplained but is now immaterial because both system-back paths use the same spec.**

A device was attached for the capture in §5. No branch other than `animations` was used.

---

## 0. User observations (reported 2026-11-19)

- Device runs **Android 16**.
- Bad animation appears with **edge swipe AND 3-button Back tap**; never with the app's own
  in-app Back arrow (the in-app arrow always produces the normal shrink).
- Perceived threshold: after roughly **2 seconds** on a screen, every subsequent Back produces
  the all-corners collapse; pressing Back well under 2 s looks normal.
- Initially reported as not tracking the finger; **corrected on closer inspection — the
  rectangular collapse does follow the thumb** during a swipe. That confirms predictive seek.
- Happens in any window; animation defect only, no crash, app stays alive.

Consequences: the finger-tracking correction rules out a system-only animation (path C) and
confirms Navigation-Compose's `SeekableTransitionState.seekTo(progress)` is driving the window.
A timer in app code is unlikely (§4.2; no 2 s constant in app or any back library). §5 shows the
wrong spec at dwell times from 831 ms upward, so there is no 2 s gate in the selected code path.

## 1. Verdict so far

| Question | Answer | Evidence |
|---|---|---|
| What does the "wrong" animation look like numerically? | `scaleOut(targetScale = 0.7f)` → **0.49 of the area**, i.e. "roughly half", shrinking from all edges | Navigation-Compose 2.10.2 source, `DefaultNavTransitions.android.kt:76-80` |
| What does the "right" animation look like? | `scaleOut(targetScale = 0.87f)` + fade, ~150 ms linear → 0.76 of area | `app/src/main/java/com/example/helper/motion/HyprMotion.kt:172,186-190` |
| Which code path selects each? | `inPredictiveBack == true` → library default 0.7 path; `false` → app's `popExitTransition` (0.87) | `NavHost.kt:884-891` (loaded from the 2.10.2 sources jar) |
| Why is the app's spec not used on the predictive path? | The app calls the **deprecated 4-transition `NavHost` overload**, which hardcodes `predictivePopExitTransition = DefaultNavTransitions.predictivePopExitTransition`. No app code overrides it anywhere. | `app/src/main/java/com/example/helper/nav/AppNavHost.kt:83-136`; `NavHost.kt:174-212` |
| Is the time-of-stay cause proven? | **No timer exists.** The wrong path was logged at dwell times 831–3103 ms, including well under 2 s. The perceived threshold is not a code gate. | §4.2, §5 |
| Runtime classification | **B — Navigation-Compose predictive back**, proven by device log | §5 |

**Classification: B (Navigation-Compose predictive back).** `PREDICTIVE_POP_EXIT ...
spec=library-default(scaleOut 0.7f)` appears on the bad gesture, together with platform events
`dispatchOnBackStarted` → `dispatchOnBackInvoked` and a callback registered as
`androidx.navigationevent.OnBackInvokedInput$...` with `mIsAnimationCallback=true`. That is the
user's thumb-tracking collapse. Path A would log `POP_EXIT` (in-app arrow); path C would log no
app transition at all.

**Root cause (proven statically and at runtime):** the app's Hyprland pop-exit spec was wired only
into `popExitTransition`. Navigation-Compose 2.10.x split back into two transitions and the
predictive one was left at the library default. The fix is in §7. This is a defect introduced by
the Navigation 2.10.x API change, not by any timer in this app.

---

## 2. Stack (verified)

| Item | Value | Source |
|---|---|---|
| Navigation-Compose | 2.10.2 | `gradle/libs.versions.toml:11,36` |
| Compose BOM | 2026.09.00 | `gradle/libs.versions.toml:10` |
| Activity-Compose | 1.13.0 | `gradle/libs.versions.toml:8` |
| AGP / Gradle / Kotlin | 9.5.0 / 9.6.0 / 2.3.21 | `gradle/libs.versions.toml:2,9`, wrapper |
| minSdk / targetSdk / compileSdk | 24 / 37 / 37 | `app/build.gradle.kts:55-57,28-30` |
| Predictive back opted in | `android:enableOnBackInvokedCallback="true"` | `app/src/main/AndroidManifest.xml:15` |
| Activities | one (`MainActivity`), no Fragments | `app/src/main/java/com/example/helper/MainActivity.kt:13`; §4.6 |

### Relevant release notes (verified)
AndroidX Navigation release notes, https://developer.android.com/jetpack/androidx/releases/navigation :
- **2.10.0 (2026-08-26):** "`NavHost` now provides an overload that accepts
  `predictivePopEnterTransition` and `predictivePopExitTransition` parameters to allow for custom
  transition during the predictive back gesture." Also: `DefaultNavTransitions` made public in
  2.10.0-alpha03.
- **2.10.1:** animation-skipping fix (sizeTransform clipping, b/550924680).
- **2.8.5 / 2.9.0-alpha05:** "Fixed an issue that caused an unexpected scale animation when using
  Navigation Compose and calling navigate in the same frame that the current animation is ending"
  (b/353294030) — already fixed well before 2.10.2, so not the cause here.

The two-transition split is therefore new in the exact major version this app uses, which is when
the app's custom pop spec stopped covering system back.

---

## 3. The selection mechanism (from the 2.10.2 sources jar, not guessed)

`NavHost.kt` (2.10.2), `finalExit`:

```
if (inPredictiveBack) {
    initialDestination.hierarchy.firstNotNullOfOrNull {
        destination.createPredictivePopExitTransition(this, swipeEdge)
    } ?: predictivePopExitTransition.invoke(this, swipeEdge)      // <-- app has none
} else if (composeNavigator.isPop.value) {
    ... ?: popExitTransition.invoke(this)                          // <-- app's HyprMotion 0.87
}
```

`inPredictiveBack` is set only from the gesture lifecycle (`NavHostEventHandler.kt`):
- `onBackStarted` / `onBackProgressed` → `inPredictiveBack = true` (gesture drag).
- 3-button/IME/non-predictive back → dispatcher calls `onBackCompleted()` directly;
  `inPredictiveBack` stays `false` → app's `popExitTransition` runs.

`DefaultNavTransitions.android.kt:76-80`:

```
predictivePopExitTransition = { scaleOut(targetScale = 0.7f) }   // no HyprMotion spec
```

`NavHostEventHandler.kt` registers with the `LocalNavigationEventDispatcherOwner` on composition
and is enabled whenever `currentBackStack.size > 1`; no timeout or dwell-time gate exists in the
library side either.

---

## 4. Static audit (file:line or "not present")

### 4.1 All back handling and its enablement over time
| Finding | Location / result |
|---|---|
| `enableOnBackInvokedCallback` | `app/src/main/AndroidManifest.xml:15` — on for the whole app, static |
| `BackHandler` (always enabled) | `homework/ui/HomeworkEdit.kt:121` and `parents/ui/AnnouncementEdit.kt:87` → `leave()` → `viewModel.close()` + `onDone()` → `popBackStack()` in `nav/AppNavHost.kt`; consumes system back as a **programmatic pop (path A)** |
| `BackHandler(enabled = showImport)` | `roster/ui/classlist/ClassListScreen.kt:90` — toggles with a dialog, not with time |
| `BackHandler(enabled = true) {}` | `exportbackup/ui/ExportScreen.kt:423` — deliberately blocks back during restore outcome; static |
| NavHost's own back handler | `nav/AppNavHost.kt:83` (implicit in `NavHost`); enabled when back-stack size > 1 |
| `PredictiveBackHandler` | **not present** |
| `OnBackPressedCallback` / `onBackPressedDispatcher` | **not present** in app source (used internally by Activity/Compose) |
| `KeyEvent.KEYCODE_BACK` / `onBackPressed` (override) | **not present** |
| `finish()` / `finishAffinity()` / `moveTaskToBack()` | **not present** in app source |
| `overridePendingTransition` / `windowAnimationStyle` | **not present** |
| Any of the above enable/disable based on elapsed time | **not present** — all enablement is composition state or back-stack size |

Note: `exportbackup/writers/SummaryPdfWriter.kt:66,156` contain `pages.finish()` / `finish()` but these
are PDF writer methods, not Activity back handling (false positives).

### 4.2 Everything time-based
| Finding | Location / result |
|---|---|
| `delay()` | `exportbackup/ui/ExportScreen.kt:426` only (`RESTART_DELAY_MILLIS`) — restore restart, unrelated |
| `withTimeout*` | `scaffold/ChipGestures.kt:63` — long-press timeout for chips, unrelated to back |
| `Handler.postDelayed` / `Timer` / `CountDownTimer` / `AlarmManager` / `WorkManager` | **not present** |
| `LaunchedEffect(Unit)` | `homework/ui/HomeworkEdit.kt:107`, `parents/ui/AnnouncementEdit.kt:89` — collect one-shot event flows; no animation state |
| `repeatOnLifecycle` | **not present** |
| `stateIn(... WhileSubscribed(5_000))` | ~15 ViewModels, e.g. `today/TodayViewModel.kt:119-121`, `homework/viewmodel/HomeworkViewModel.kt:111-113`, `roster/viewmodel/StudentPageViewModel.kt:84`, `parents/viewmodel/ParentsViewModel.kt:103-105`. These stop/restart data flows 5 s after the view stops; they do not touch transitions or back handling |
| Session/auto-refresh/auto-save ticker | **not present**. `LocalDate.now()` occurrences (e.g. `today/TodayViewModel.kt:32`, `nav/AppNavHost.kt:228,233`) change at midnight only |
| `System.currentTimeMillis()` | All are timestamps for DB rows / filenames / click throttles (`nav/NavThrottle.kt:14`, `motion/Throttle.kt:14`, repositories) — none gates animation |
| `Settings.Global.ANIMATOR_DURATION_SCALE` | Was **read once** in `ProvideHyprMotion`'s `remember(context)` at audit time — time-*independent*, so could not explain the reported large shrink. Now observed via a `ContentObserver` (see `motion/HyprMotion.kt`); `MotionDiag` does not log it — see §9 suspect 3 |

**Conclusion:** there is no code in this app that changes a back animation as a function of time-on-screen.
The time correlation must come from input method / gesture lifecycle / transition settling, not a timer.

### 4.3 Screen internals (stuck scale / alpha)
| Finding | Location / result |
|---|---|
| `animateContentSize` | **not present** |
| Default `SizeTransform` in app code | **not present**; `motion/SharedMotion.kt` uses `BoundsTransform` only |
| `graphicsLayer` scale | `motion/MotionEffects.kt:39-41` (`pressScale`), `:62-64` (`popOnChange`), `:78` (`shake` translation) |
| `Animatable` / `animate*AsState` never reset | **not present** — `pressScale` targets `1f` on release (`:35`), `popOnChange` animates back to `targetScale` (default `1f`, `:59-60`), `shake` ends at `0f` (`:76`). No stuck scale |
| `rememberSaveable` animation state | **not present** — only `examsresults/ui/StudentExamHistory.kt:97` remembers a selected subject id |
| Screen-level `AnimatedContent` | `attendance/ui/AttendanceScreen.kt:318-326` (counter only) |
| Screen-level `AnimatedVisibility` | `scaffold/AppScaffold.kt:68-82` (bottom bar), `motion/ScreenTakeover.kt:28-34` (homework/announcement takeover) |

### 4.4 Transition definitions (derivation / mutability)
| Finding | Location / result |
|---|---|
| Conditional / mutable-derived specs | **not present**. `motion/MotionNav.kt:25-55` picks a fixed HyprMotion leaf from route types only |
| `HyprMotion.SPEED_SCALE` | Declared `var` at `motion/HyprMotion.kt:73`; no other assignment in the repo — effectively constant `1f` |
| Reduce-motion flag | `motion/HyprMotion.kt:47,52-60`; provided once at `MainActivity.kt:19` via `ProvideHyprMotion`, read at `nav/AppNavHost.kt:62` and in screen composables — **not observed live** |
| What "reduced" returns | `EnterTransition.None` / `ExitTransition.None` — `motion/HyprMotion.kt:180,187,193-197,201-204,208,215`. If reduce-motion were the cause, the screen would not shrink at all; the reported large shrink rules this out |
| Per-destination transition overrides | **not present** — no `enterTransition`/`exitTransition`/`pop*` argument appears on any `composable()` call; all are host-level |

### 4.5 Navigation structure and route → lambda table
| Finding | Location / result |
|---|---|
| Nested NavHosts / nested graphs | **not present** — one `NavHost` at `nav/AppNavHost.kt:83` |
| `dialog()` / `bottomSheet()` destinations | **not present** |
| `popUpTo` | `nav/Routes.kt:99-102` (tab navigation), `nav/AppNavHost.kt:264,280,335` (exam flows, mark entry) — all forward-navigation options, not back animation |
| `launchSingleTop` / `saveState` / `restoreState` | `nav/Routes.kt:100-103` — tab navigation only |
| Route → enter/back lambda | **Every** route uses the same host lambdas: forward `MotionNav.enter` (`popinIn` for detail, `workspaceIn` for tab↔tab); back `MotionNav.popExit` (`popinOut` for detail, `workspaceOut` for tab↔tab) — `nav/AppNavHost.kt:87-110`, `motion/MotionNav.kt:25-55`. No per-route variation |
| Detail routes with an in-app back button | Most detail screens (`onBack`/`onDone` → `popBackStack()`), e.g. `nav/AppNavHost.kt:177,188,202,216,237,252,267,283,303,305,315,331,332,343` |
| Screens that also have a system-back `BackHandler` | `HomeworkEdit.kt:121`, `AnnouncementEdit.kt:87` (takeovers), `ClassListScreen.kt:90` (import), `ExportScreen.kt:423` (blocked) |

### 4.6 Activity / XML animations
Single `MainActivity` (`MainActivity.kt:13`); no FragmentActivity/AppCompatActivity/Fragment classes;
no `res/anim` or `res/animator` folders. **Not present.**

---

## 5. Runtime capture — EXECUTED 2026-11-19 (Android 16, real device)

Command actually run by the device owner:

```
adb logcat -v time | grep -i -E "backnav|predictive|OnBackInvoked" | tee /tmp/backplatform.txt
```

**Capture caveat:** that filter matches `PREDICTIVE_*` but *not* `POP_EXIT`, `POP_ENTER`, `ENTER`,
`EXIT` or `DEST_CHANGED`. Rows with no `PREDICTIVE` line are therefore **unclassified**, not proof of
path C. Re-run the `MotionDiag:D` filter from §6 to fill those in.

Route pair throughout: `class/{classId}` → `students`.

| # | time | input (from platform events) | `sinceScreenEntered` | last MotionDiag line | class | log excerpt |
|---|---|---|---|---|---|---|
| 1 | 09:28:52.719 | tap, no `dispatchOnBackStarted`, `triggerBack=false` | n/a | none captured | ? | `Received backNavigationInfo: TYPE_CALLBACK ... Trigger back without dispatching to animator ... onBackNavigationDone backType=4, triggerBack=false` |
| 2 | 09:28:54.285–.327 | **gesture**: `dispatchOnBackStarted` + 3 progress events | 1594–1598 ms | `PREDICTIVE_POP_EXIT ... spec=library-default(scaleOut 0.7f)` | **B** | `I/ShellBackPreview: dispatchOnBackStarted ... onGestureStarted:87` → `D/MotionDiag: PREDICTIVE_POP_EXIT ... swipeEdge=1 spec=library-default(scaleOut 0.7f)` → `dispatchOnBackInvoked ... handleFinishKeyAction:324` |
| 3 | 09:29:03.563–.582 | tap, no `dispatchOnBackStarted` | n/a | none captured | ? | `Trigger back without dispatching to animator ... triggerBack=true ... onBackInvoked, callback=androidx.navigationevent.OnBackInvokedInput...` |
| 4 | 09:29:08.760–.841 | tap, no `dispatchOnBackStarted` | n/a | none captured | ? | same as row 3 |
| 5 | 09:29:13.940–14.139 | gesture, 3 progress events | 3099–3103 ms | same as row 2 | **B** | `PREDICTIVE_POP_EXIT ... sinceScreenEntered=3099ms ... spec=library-default(scaleOut 0.7f)` |
| 6 | 09:29:17.222–17.438 | gesture, 3 progress events | 1538–1543 ms | same as row 2 | **B** | `PREDICTIVE_POP_EXIT ... sinceScreenEntered=1538ms ... spec=library-default(scaleOut 0.7f)` |
| 7 | 09:29:21.181–21.384 | gesture, 3 progress events | 1578–1582 ms | same as row 2 | **B** | `PREDICTIVE_POP_EXIT ... sinceScreenEntered=1578ms ... spec=library-default(scaleOut 0.7f)` |
| 8 | 09:29:25.641–25.839 | gesture, 3 progress events | 2106–2110 ms | same as row 2 | **B** | `PREDICTIVE_POP_EXIT ... sinceScreenEntered=2106ms ... spec=library-default(scaleOut 0.7f)` |
| 9 | 09:29:30.187–30.780 | gesture, 3 progress events | 1348–1352 ms | same as row 2 | **B** | `PREDICTIVE_POP_EXIT ... sinceScreenEntered=1348ms ... spec=library-default(scaleOut 0.7f)` |
| 10 | 09:29:33.482–35.527 | gesture, 3 progress events | **831–834 ms** | same as row 2 | **B** | `PREDICTIVE_POP_EXIT ... sinceScreenEntered=831ms ... spec=library-default(scaleOut 0.7f)` |
| 11 | 09:29:39.022 | — (tab root reached) | — | — | — | `setTopOnBackInvokedCallback ... android.app.Activity$$ExternalSyntheticLambda0 ... mIsAnimationCallback=false` (nav callback removed) |

Additional platform proof of the input path:

```
09:28:48.217 D/WindowOnBackDispatcher: setTopOnBackInvokedCallback (unwrapped):
    android.app.Activity$$ExternalSyntheticLambda0@d0c36bc        mPriority=-1, mIsAnimationCallback=false
09:28:50.946 D/WindowOnBackDispatcher: setTopOnBackInvokedCallback (unwrapped):
    androidx.navigationevent.OnBackInvokedInput$createOnBackAnimationCallback$1@4771be2
                                                                   mPriority=0,  mIsAnimationCallback=true
09:28:52.720 D/ShellBackPreview: Received backNavigationInfo: TYPE_CALLBACK (4), ... mAnimationCallback=true
```

**What this settles:** the collapse is the app's own predictive path (B), not a system-only
animation (C) and not the in-app arrow (A). It selects the wrong spec on *every* captured gesture,
including at 831 ms — so the reported "about 2 seconds" is not a gate in this code path. The fix in
§7 makes the distinction irrelevant.

**Still to capture** (only needed for completeness, not for the fix): rows 1/3/4 with the
`MotionDiag:D` filter, an in-app-arrow row, lock/unlock, and the long-wait rows (10 s–15 min).

---

## 6. Instrumentation added (debug only)

All guarded by `if (BuildConfig.DEBUG)`; `buildConfig = true` was enabled in `app/build.gradle.kts`
because AGP 9 defaults it to `false`. BuildConfig is generated as bytecode/JAR in AGP 9, so no
source file is added to the release tree.

| File:line | What it logs |
|---|---|
| `motion/MotionDiag.kt:1-73` | `MotionDiag` helper: tag, `transition()`, `destinationChanged()`, `local()`; `Clock` records time of last destination change |
| `nav/AppNavHost.kt:66-80` | `OnDestinationChangedListener` with back-stack size and full stack; `DEST_CHANGED ... stayed=<dwell>` |
| `nav/AppNavHost.kt:87-110` | `ENTER` / `EXIT` / `POP_ENTER` / `POP_EXIT` — app lambdas, with route pair and `sinceScreenEntered` |
| `nav/AppNavHost.kt:114-136` | `PREDICTIVE_POP_ENTER` / `PREDICTIVE_POP_EXIT`, with `swipeEdge` and the selected spec; now delegate to `MotionNav` (see §7) |
| `scaffold/AppScaffold.kt:65-68` | `BAR_VISIBILITY_CHANGED` |
| `motion/ScreenTakeover.kt:28-32` | `TAKEOVER_VISIBILITY_CHANGED` |
| `attendance/ui/AttendanceScreen.kt:322-326` | `COUNTER_ANIM` inside the `AnimatedContent` `transitionSpec` |

Verification after instrumentation: `./gradlew :app:compileDebugKotlin` ✅ and
`./gradlew :app:testDebugUnitTest` ✅ (both green, zero new warnings).

Full capture command for the rows §5 could not classify:

```
adb logcat -v time MotionDiag:D ActivityTaskManager:I WindowManager:I *:S
```

---

## 7. Smallest fix — APPLIED

In `nav/AppNavHost.kt`, after §5 proved path B, the two predictive lambdas were changed from
`DefaultNavTransitions.*` to the same `MotionNav` helpers the in-app back arrow already uses:

```kotlin
predictivePopEnterTransition = { swipeEdge -> /* diag */ MotionNav.popEnter(this, initialState, targetState, reduceMotion) },
predictivePopExitTransition  = { swipeEdge -> /* diag */ MotionNav.popExit (this, initialState, targetState, reduceMotion) },
```

`swipeEdge` is still accepted by the API but ignored — the Hyprland leaves have no edge-specific
variant. The `DefaultNavTransitions` import was removed. Nothing else changed: no manifest change,
no library downgrade, no `BackHandler` change, no screen-level animation change.

Why this is safe under predictive seek: Navigation 2.10.2 drives these specs through
`SeekableTransitionState.seekTo(progress)` (`NavHost.kt:927-931`). The library default it replaces is
also a plain finite `scaleOut(targetScale = 0.7f)` with a spring
(`DefaultNavTransitions.android.kt:76-80`), so `HyprMotion.popinOut`'s finite
`tween(149 ms, Linear) scaleOut(0.87f) + fadeOut` is the same kind of spec and remains seekable —
it will still follow the thumb, just settle at 87% with a fade instead of 70%.

Rejected alternative: removing `android:enableOnBackInvokedCallback` would delete the feature
rather than fix the animation, and would leave in-app and system back inconsistent.

Verification: `./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest
--console=plain` → **BUILD SUCCESSFUL** (48 tasks, 9 executed, 39 up-to-date), zero warnings.

**Regression guard:** the debug log now prints `spec=HyprMotion` on predictive back. After installing
the rebuilt APK, repeat §5 row 2: it must show `PREDICTIVE_POP_EXIT ... spec=HyprMotion`; a return of
`spec=library-default(scaleOut 0.7f)` is an immediate regression. Also confirm the window still
tracks the thumb and ends at roughly 76% area with a fade. The existing unit-test suite does not
cover Compose transitions and no Compose-test dependency was added just for this.

---

## 7.1 Post-fix device verification (EXECUTED 2026-11-19)

The rebuilt debug APK was installed and the same capture re-run. Every predictive back now
selects the HyprMotion spec; no `library-default(scaleOut 0.7f)` line appeared.

```
11-19 09:39:40.812 D/MotionDiag: PREDICTIVE_POP_EXIT
    from=exam/{examId}/class/{classId} to=exams/{classId}
    t=69897211 sinceScreenEntered=2034ms swipeEdge=1 spec=HyprMotion
11-19 09:39:42.324 D/MotionDiag: PREDICTIVE_POP_EXIT
    from=exams/{classId} to=class/{classId}
    t=69898723 sinceScreenEntered=1379ms swipeEdge=1 spec=HyprMotion
11-19 09:39:43.965 D/MotionDiag: PREDICTIVE_POP_EXIT
    from=class/{classId} to=students
    t=69900364 sinceScreenEntered=1083ms swipeEdge=1 spec=HyprMotion
```

| time | route pair | dwell | spec | class |
|---|---|---|---|---|
| 09:39:40.812–.818 | `exam/{examId}/class/{classId}` → `exams/{classId}` | 2034–2040 ms | `HyprMotion` | B (fixed) |
| 09:39:42.324–.333 | `exams/{classId}` → `class/{classId}` | 1379–1388 ms | `HyprMotion` | B (fixed) |
| 09:39:43.965–.971 | `class/{classId}` → `students` | 1083–1089 ms | `HyprMotion` | B (fixed) |
| 09:39:47.251–.255 | `class/{classId}` → `students` | 1369–1373 ms | `HyprMotion` | B (fixed, gesture then cancelled: `triggerBack=false`) |
| 09:39:51.489–.493 | `exam/{examId}/class/{classId}` → `exams/{classId}` | 2512–2516 ms | `HyprMotion` | B (fixed) |
| 09:39:54.386–.390 | `exams/{classId}` → `class/{classId}` | 2788–2792 ms | `HyprMotion` | B (fixed) |
| 09:39:58.030–.035 | `class/{classId}` → `students` | 3249–3254 ms | `HyprMotion` | B (fixed) |
| 09:40:03.346–.350 | `class/{classId}` → `students` | 3354–3357 ms | `HyprMotion` | B (fixed) |
| 09:40:06.810–.814 | `class/{classId}` → `students` | 1501–1505 ms | `HyprMotion` | B (fixed) |
| 09:40:11.390–.395 | `class/{classId}` → `students` | 3284–3289 ms | `HyprMotion` | B (fixed) |
| 09:40:13.969–.973 | `class/{classId}` → `students` | 1108–1112 ms | `HyprMotion` | B (fixed) |
| 09:40:18.113–.118 | `class/{classId}` → `students` | 1247–1252 ms | `HyprMotion` | B (fixed) |

The two presses at 09:39:47.899 and 09:39:47.954 ended with `triggerBack=false` and produced no
transition line — a cancelled back commits no pop, so that is expected, not a regression.

Device owner reports the collapse is gone and the animation now matches the in-app arrow. The
check at 2034 ms and 3357 ms covers the reported "about 2 seconds" threshold.

## 8. Unverified after the fix

1. **The "about 2 seconds" perception.** §5 row 10 shows the wrong path at 831 ms, and §7.1 shows
   the fixed path at dwell times from 1083 ms to 3357 ms, so there is no code gate. Most likely the
   user's immediate-back samples used a different input method, or the still-running `popinIn`
   masked the collapse. Only ~100 ms and long waits (10 s+) remain untested.
2. **Gesture vs 3-button** — the tap rows (§5 rows 1/3/4) were not captured with the `MotionDiag`
   filter, so we cannot say which of A/B they took. The user reports both look wrong; after the fix
   both must show the HyprMotion spec.
3. **Lock/unlock and long waits** (10 s–15 min) — not captured. No code path found that would make
   them differ (§4.2), but not empirically ruled out.
4. **Reduce-motion state** — not checked on the device. It would remove the shrink entirely, so it
   cannot explain the reported large collapse. The setting is now observed live in `HyprMotion.kt`;
   check with `adb shell settings get global animator_duration_scale` if desired.
5. **3-button and in-app-arrow post-fix rows** — only gesture backs were captured after the fix;
   both now share the same `MotionNav` spec by construction, but neither was logged on device.

## 9. Remaining suspects (all demoted; B was confirmed)

1. **Interrupted enter transition** — would only change how the seek *starts*, not which spec is
   selected. §5 row 10 at 831 ms already shows the wrong spec with `popinIn` (≈410 ms) long settled.
   Confirm/resolve with the 100/500/1000/2000 ms rows after the fix.
2. **Input-method mix** — real, but now harmless: both predictive and non-predictive lambdas call
   `MotionNav`, so gesture, 3-button and in-app arrow share one spec.
3. **Reduce-motion state** — `ANIMATOR_DURATION_SCALE == 0` gives `ExitTransition.None`, not a
   large shrink. Ruled out by the symptom.
4. **A `BackHandler` consuming back** — `HomeworkEdit.kt:121`, `AnnouncementEdit.kt:87`,
   `ClassListScreen.kt:90`, `ExportScreen.kt:423`. The reported screens are ordinary detail routes
   with none of these; static audit §4.1 found no time-dependent enablement.
5. **System-only animation (C)** — ruled out by the finger-tracking correction and by the
   `PREDICTIVE_POP_EXIT` lines. Cannot apply on a detail route where the nav callback is top.
