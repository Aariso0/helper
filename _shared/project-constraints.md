# Project-wide constraints — the factory every module inherits

Filled once, early. Every module's `# In Use` / `# Excluded` should agree with this file; if research reveals a reason to change something here, say so to the user rather than overriding it quietly in one module's card.

## Must use
- Kotlin — the README commits the project to it; standard for Android.
- Jetpack Compose — current standard Android UI toolkit, chosen for fast UI iteration.

## Must avoid
- Any backend, server, or cloud SDK (Firebase, Supabase, etc.) — the prototype is deliberately local-only; the client decides on a server after seeing it.
- Accounts, login, sync, push notifications, parent-facing screens — all explicitly out of scope ("Skip for now" in the README).
- Internet permission unless a feature truly needs it — the prototype works fully offline.
- Real student data anywhere in the repo, screenshots, or demo files — use fake names (children's data privacy).

## Platform & scale
- Android phones, single teacher, ~5 features, roughly 40–60 students per class (exact numbers unknown). Fully on-device, offline, no deployment context beyond sharing an APK.
- UI efficiency is priority #1: fewest taps, smart defaults, one-handed phone-first, autosave/undo.

## Notes
- Prototype, not a finished product: every decision is judged by "does this save the teacher time or taps?"
- UI language: **English only** (user decision) — no localization in the prototype; retrofit later if the client asks.
- PPT handling: **PDF-first, PPT tolerated** (user decision) — UI recommends PDF; PPT attaches/shares as-is and is never previewed in-app.
- Attendance granularity: **per class per day** (user decision), many classes supported; the data model keeps an optional `period` field so a period dimension can be added later without migration.
- Export (CSV/Excel, PDF) must stay possible — the client must never feel locked in.
- Optional cheap habits for a later server phase: device-generated unique IDs, created/updated timestamps, data access behind one layer.
- Scope is the local prototype only (confirmed by user). A future server phase enters through a redo — never as half-planned cards now. **Updated 2026-10-20 by user decision:** one deliberate exception exists — `modules/server-comms.md` is a `status: planned` placeholder card for the **v3** server phase. It carries no stack, no edges, and no research; nothing server-related is built or researched before v3 regardless.

## Unresolved
- Client's country (privacy rules depend on it — likely India, unconfirmed).
- Client's phone / Android version → sets min SDK (not decided).

