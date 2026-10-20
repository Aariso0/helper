# Build protocol — for the team lead and every implementing agent

The blueprint is finished enough to build from. This file is how several agents build it at once, talk to each other, and leave a trail so any agent can pick up where another ran out of tokens.

The blueprint folders (`modules/`, `_index/`, `_shared/`, `_templates/`) stay **docs-only**. Code goes in the project's source folders at the project root, laid out as each card's `File & folder layout` says.

## Roles

- **Lead** — starts the team, assigns modules, mirrors `build:` values into `_index/MODULES.md`, regenerates `_index/CONNECTIONS.md` when a card's `consumes` / `produces` changes, answers stack questions by asking the user.
- **Teammate** — owns exactly one module. Edits only that module's card and that module's source folder.

## Starting (lead)

1. Read `CLAUDE.md` → `_index/MODULES.md` → `_index/CONNECTIONS.md`.
2. `mkdir -p chat/grp_chat` — the group chat log's home.
3. Pick modules with `status: drafted` and `build` not yet `built`. Producers before consumers (a module that consumes nothing goes first). Keep the team small — each teammate is a full session and costs tokens.
4. Spawn one teammate per module. Each is told: its module slug, "read `_shared/build-protocol.md` and `modules/<slug>.md`", and the names of the teammates whose modules it consumes from or produces for.
5. Log a `TEAM-START` line (see Group chat).

## Teammate loop

1. Read your whole card. For neighbours, read **only** their `Connections` section.
2. **Claim:** set `build: in-progress` in your card's frontmatter, fill `Owner` in `Build progress`, log `CLAIM`.
3. Build in small chunks, following `Setup sequence` and `Exact stack` literally.
4. **Checkpoint after every chunk** — never batch to the end. Update `Build progress` (move items from Remaining to Coded, with file paths), bump `Last checkpoint` and `last-updated`. A session can die at any moment; the card must always be true.
5. **Talk across seams.** Before coding against a neighbour's interface, or when you change one, message the owning teammate directly and agree the shape first. Log every message.
6. **Never edit another module's card.** Ask its owner. If an interface changes, the owner updates their own `Connections` and `consumes` / `produces`, then tells the lead.
7. **Never deviate from `Exact stack`** (different library, version, or approach). Stop, add an `Open questions` line, message the lead, log `BLOCKED`. The user decides; a stack change is a redo through the blueprint skill, not a quiet patch.
8. **Done:** `Remaining` empty, everything in `File & folder layout` coded, set `build: built`, log `DONE`.
   - **The gate:** `./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest --console=plain` must be green with **zero warnings**. Warnings are defects — fix them, never suppress them (`@Suppress`, blanket `@OptIn`, `-nowarn`).
   - Then the bookkeeping sweep: card (`build:` + `Build progress`) → `_index/MODULES.md` → `DONE` line in `chat/grp_chat/` → `chat/CURRENT.md` + a journal entry → commit only your module's files. `.idea/` is never committed.
9. **Running low on tokens or context:** finish the current chunk, write the checkpoint and a `Handoff note` (what you were about to do, any trap you found), log `HANDOFF`, stop. Do not start a new chunk you can't checkpoint.

## Group chat log

- Folder: `chat/grp_chat/` — one file per day, `YYYY-MM-DD.md`, append-only.
- One line per entry, newest at the bottom:

```
[2026-10-08T14:32:05+05:30] auth → billing: session-token payload is { userId, role, exp }, signed JWT, header Authorization: Bearer
[2026-10-08T14:33:41+05:30] billing → auth: need `plan` in the payload too, ok?
[2026-10-08T14:34:10+05:30] auth → billing: no, keep it out of the token; fetch plan from billing's own store
[2026-10-08T15:02:00+05:30] SYSTEM auth: CHECKPOINT login route done, refresh route next
```

- Get the timestamp from the shell, never from memory: `date -Iseconds`.
- Append with one command per entry so lines don't interleave:
  `echo "[$(date -Iseconds)] <from> → <to>: <message>" >> "chat/grp_chat/$(date +%F).md"`
- Never rewrite or delete earlier lines.
- Log: every message you send to a teammate or the lead, every decision about an interface, and the markers `TEAM-START`, `CLAIM`, `CHECKPOINT`, `BLOCKED`, `DONE`, `HANDOFF` (as `SYSTEM <slug>: <marker> <short note>`).

## Resuming (any new agent, any session)

1. `CLAUDE.md` → `_index/MODULES.md` (blueprint `status` and `build` per module) → `_index/CONNECTIONS.md`.
2. Tail the latest file in `chat/grp_chat/` (last ~50 lines) for what was just discussed.
3. Open only the card you're taking over and read its `Build progress` — `Coded`, `Remaining`, `Handoff note`. Trust it; don't re-derive it by re-reading all the code.
4. If a card's `build:` and `MODULES.md` disagree, the card wins; fix `MODULES.md`.
5. Claim the module (Teammate loop step 2) and continue from the first `Remaining` item.

## Launch prompt (user pastes this into Claude Code with agent teams enabled)

```
Create an agent team to build this project from its blueprint.
Read CLAUDE.md and _shared/build-protocol.md first, and follow them exactly.
Lead: create chat/grp_chat/, then spawn one teammate per drafted module,
starting with modules that consume nothing. Each teammate owns one module,
talks directly to the teammates it connects with, logs every message to the
group chat, and checkpoints into its card's Build progress after every chunk.
Do not change any module's Exact stack; ask me instead.
```
