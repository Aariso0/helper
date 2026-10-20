---
module: {slug}
status: planned   # planned · researching · drafted · needs-redo
consumes: []      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: []      # what this module exposes to others (use _shared/glossary.md terms)
build: not-started   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: {YYYY-MM-DD}
---

# {Module name}

{Exactly two lines: what this module does. Not three.}

## # In Use
- {library / framework / service / repo} — {why, half a sentence}

## # Excluded
- {library / approach} — {why: user-specified, or ruled out during research, including a runner-up that lost a close call}

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| {e.g. runtime} | `{exact name}` | `{exact version}` | {one clause} |
| {e.g. framework} | `{exact name}` | `{exact version}` | {one clause} |
| {e.g. data store} | `{exact name}` | `{exact version}` | {one clause} |

Every row is a decision, not a shortlist. If two options were close, the loser goes in `# Excluded` with the reason — nothing is left as an "or" here.

## File & folder layout
```
{module-slug}/
├─ {exact file or folder name}   {one-line purpose}
├─ {exact file or folder name}   {one-line purpose}
└─ ...
```
Names and purposes only — never file contents.

## Setup sequence
1. {Ordered, concrete step — e.g. an exact init or install command.}
2. {Next step.}
3. {Next step.}

## Why this shape
{The reasoning: why this exact stack over what got excluded, and how it fits the rest of the project's stack and the modules it touches.}

## Connections
- **Consumes:** {what, from which module, in what shape}
- **Produces:** {what, in what shape}
- **Talks to via:** {REST / events / shared store / function boundary / etc.}

## Open questions
- {anything genuinely undecided — kept to an absolute minimum; a finished card should have none}

## Build notes for the implementing agent
{Ordering, gotchas, things to watch for. Guidance, not code.}

## Build progress
{Left blank by the blueprint skill. Implementing agents fill and update this after every chunk of work.}
- **Owner:** {agent / teammate name, or none}
- **Coded:**
  - {`path/to/file` — what is done in it}
- **Remaining** (next first):
  1. {next piece of work}
- **Blocked on:** {nothing / what and who}
- **Handoff note:** {what the next agent should know — where you stopped, any trap}
- **Last checkpoint:** {ISO timestamp}
