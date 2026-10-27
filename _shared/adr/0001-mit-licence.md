# 0001 — MIT licence, permissive dependencies only

- **Status:** accepted
- **Date:** 2026-10-26
- **Deciders:** user
- **Source:** user answer in session, 26-10-2026: "open source also add an license file with an MIT license and choose libraries that support it"

## Context

The project had no licence model recorded. The app ships as an APK to one teacher, and the source is now public. Dependency licences must be compatible with the project licence, because the APK bundles the libraries.

## Decision

- The project is licensed **MIT** (`LICENSE` at the repository root).
- Runtime dependencies must be **permissive or permissive-with-notice**: MIT, ISC, BSD-2/3, Apache-2.0. Apache-2.0 is allowed because it is one-way compatible into an MIT project; the Apache licence text and NOTICE files must travel with the distribution, tracked in `THIRD-PARTY-NOTICES.md`.
- Copyleft without a linking exception (GPL-2.0/3.0, AGPL-3.0, SSPL), source-available licences, and field-of-use restrictions are **disallowed** at runtime.
- Test-only tools may carry other licences (JUnit 4 is EPL-1.0) because they are not distributed.

## Consequences

- `desugar_jdk_libs` (GPL-2.0 **with Classpath Exception**) is acceptable as a build/runtime library because the exception permits linking with independent modules; its notice is recorded in `THIRD-PARTY-NOTICES.md`. Flagged to the user as the one copyleft-adjacent dependency.
- Every new runtime dependency needs its licence verified at the source (repo `LICENSE` or registry page), not from memory, before it is added.
- `LICENSE` carries a placeholder copyright holder (`helper contributors`); the user may replace it with their name.
- The licence is a working decision, not legal advice; a real distribution with a commercial client would get a lawyer's read.
