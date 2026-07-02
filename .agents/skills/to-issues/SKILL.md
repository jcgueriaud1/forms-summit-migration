---
name: to-issues
description: Break a plan, spec, or PRD into independently-grabbable issues on the project issue tracker using tracer-bullet vertical slices.
disable-model-invocation: true
---

# To Issues

Break a plan into independently-grabbable issues using vertical slices (tracer bullets).

The issue tracker and triage label vocabulary should have been provided to you — run `/setup-matt-pocock-skills` if not.

## Process

### 1. Gather context

Work from whatever is already in the conversation context. If the user passes an issue reference (issue number, URL, or path) as an argument, fetch it from the issue tracker and read its full body and comments.

### 2. Explore the codebase (optional)

If you have not already explored the codebase, do so to understand the current state of the code. Issue titles and descriptions should use the project's domain glossary vocabulary, and respect ADRs in the area you're touching.

Look for opportunities to prefactor the code to make the implementation easier. "Make the change easy, then make the easy change."

### 3. Draft vertical slices

Break the plan into **tracer bullet** issues. Each issue is a thin vertical slice that cuts through ALL integration layers end-to-end, NOT a horizontal slice of one layer.

<vertical-slice-rules>

- Each slice delivers a narrow but COMPLETE path through every layer (schema, API, UI, tests)
- A completed slice is demoable or verifiable on its own
- Any prefactoring should be done first

</vertical-slice-rules>

### 4. Quiz the user

Present the proposed breakdown as a numbered list. For each slice, show:

- **Title**: short descriptive name
- **Blocked by**: which other slices (if any) must complete first
- **User stories covered**: which user stories this addresses (if the source material has them)

Ask the user:

- Does the granularity feel right? (too coarse / too fine)
- Are the dependency relationships correct?
- Should any slices be merged or split further?

Iterate until the user approves the breakdown.

### Gate for Forms-migration PRDs (before publishing)

When the source is a Forms→Vaadin migration PRD (there is a structural spec under
`docs/generated/`), the **disposition ledger is a hard gate** (ADR-0015) — it
extends "nothing dropped silently" below construct grain, where the v2 leaks
happened (a default, an editability mode, a `:GLOBAL` contract):

1. Generate/refresh the ledger from the spec, carrying prior decisions forward:

   ```bash
   python3 .agents/skills/forms-xml-analyzer/scripts/disposition_ledger.py \
       docs/generated/<FORM>.spec.json --merge docs/generated/<FORM>.ledger.json \
       --out docs/generated/<FORM>.ledger.json --md docs/generated/<FORM>.ledger.md --check
   ```

2. **If the check fails, stop.** Work the unassigned rows with the user — each
   must become `migrated-as`, `re-decided` (with `reason` + `decidedBy`),
   `deferred` (with a tracking ref), or `obsolete` (with the rule id). Never
   assign a disposition for the user on a judgment row; propose, then record.
3. **Every build ticket lists the ledger rows it covers** (a `Covers:` line of
   row ids). A row no ticket covers and no other disposition explains is a leak
   the gate exists to catch.
4. **Field fidelity table.** A ticket that touches items embeds, per item, the
   spec's decision-prone facts so the builder implements against a matrix, not
   prose: default (`initializeValue`), editability mode (editable / read-only /
   **insert-only** when `updateAllowed=false, insertAllowed=true`), required,
   format mask, visibility, `copyValueFromItem`. Pull the values from the spec —
   never from memory.
5. **No unverified absence claims.** A ticket (and the code built from it) may
   claim "Forms has no X" only with a spec citation (`<FORM>.spec.json` — the
   element or its absence is checkable). `migration-review` verifies these
   claims; an uncited absence claim is a review finding.

### 5. Publish the issues to the issue tracker

For each approved slice, publish a new issue to the issue tracker. Use the issue body template below. These issues are considered ready for AFK agents, so publish them with the correct triage label unless instructed otherwise.

Publish issues in dependency order (blockers first) so you can reference real issue identifiers in the "Blocked by" field.

<issue-template>
## Parent

A reference to the parent issue on the issue tracker (if the source was an existing issue, otherwise omit this section).

## What to build

A concise description of this vertical slice. Describe the end-to-end behavior, not layer-by-layer implementation.

Avoid specific file paths or code snippets — they go stale fast. Exception: if a prototype produced a snippet that encodes a decision more precisely than prose can (state machine, reducer, schema, type shape), inline it here and note briefly that it came from a prototype. Trim to the decision-rich parts — not a working demo, just the important bits.

## Acceptance criteria

- [ ] Criterion 1
- [ ] Criterion 2
- [ ] Criterion 3

## Blocked by

- A reference to the blocking ticket (if any)

Or "None - can start immediately" if no blockers.

</issue-template>

Do NOT close or modify any parent issue.
