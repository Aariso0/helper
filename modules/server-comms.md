---
module: server-comms
status: planned   # planned · researching · drafted · needs-redo
consumes: []      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: []      # what this module exposes to others (use _shared/glossary.md terms)
build: not-started   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-20
---

# Server comms

Everything about how the app talks to a server: transport, API shape, auth, and sync protocol between the on-device data layer and a future backend. **Placeholder card — v3, not now.**

## Status of this card
- **This is a stub, not a drafted card.** Created 2026-10-20 at the user's request so the v3 server phase has a home in the blueprint. The app is currently at **v0** (local prototype).
- **Do not research, draft, or build this module before v3.** No web research has been done and none should be spent here yet — the module loop starts only when the user says the server phase begins.
- Until then the project-wide rule stands: no backend, server, or cloud SDK anywhere in the prototype ([`_shared/project-constraints.md`](../_shared/project-constraints.md)).

## # In Use
- (empty — research not started; deliberately so)

## # Excluded
- (nothing researched yet; per-module exclusions get filled during the v3 research pass)

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| — | — | — | Not researched on purpose — deferred to v3. This card becomes a real stack table through the normal module loop (research → one decision per concern) before any build. |

## File & folder layout
```
server-comms/
└─ (TBD at v3 — layout follows the stack the research pass picks)
```

## Setup sequence
1. **v3 trigger:** user opens the server phase → run the module loop on this card (set `researching`, web research per `_shared/project-constraints.md` filters, then `drafted`).
2. Reconcile `Connections` with `core-data` (`data-repositories` is the seam a sync layer must sit behind) and regenerate `_index/CONNECTIONS.md`.
3. Only then may build work begin, via `_shared/build-protocol.md`.

## Why this shape
The card exists purely so the future server work has a reserved slot, defined edges, and an honest `status: planned` ledger line instead of being invented ad hoc mid-v3. The README already parks the offline-first/last-write-wins research for this phase; that research belongs here when v3 starts.

## Connections
- **Consumes:** TBD at v3 (expected seam: `core-data`'s `data-repositories` — the "one layer" habit from the README; not confirmed until drafted)
- **Produces:** TBD at v3 (nothing may consume this module before then)
- **Talks to via:** TBD at v3 (network transport — undecided, unresearched)

## Open questions
- The whole card is open by design. First v3 question: server/hosting choice (README parks Firebase vs Supabase vs own server as a client decision).

## Build notes for the implementing agent
**Do not build this module before v3.** If you are a v0–v2 builder, stop: this card must read `status: drafted` before anyone touches it. If it still says `planned`/`researching`, the research pass has not happened yet — that is a blueprint task, not a code task.

## Build progress
- **Owner:** none
- **Coded:**
- **Remaining** (next first):
  1. Nothing until v3.
- **Blocked on:** user opening the v3 server phase
- **Handoff note:**
- **Last checkpoint:**
