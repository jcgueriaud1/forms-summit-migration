---
name: migration-to-issues
description: Cut the build tickets for a Forms→Vaadin migration PRD. Runs the disposition-ledger gate first (ADR-0015), then composes the generic to-issues skill for the breakdown and publishing, layering the migration-specific ticket requirements on top — Covers lines of ledger-row ids, per-field fidelity tables, spec-cited absence claims, acceptance criteria derived from ledger rows, and ref-liveness. Use instead of plain to-issues whenever the source PRD migrates a form (there is a structural spec under docs/generated/).
disable-model-invocation: true
---

# migration-to-issues

`to-issues` is a generic skill and stays generic — this project does not modify
it. This skill **wraps** it for Forms→Vaadin migration PRDs: run the
disposition-ledger gate, then follow the `to-issues` process end to end, adding
the migration-specific requirements below to every build ticket. Nothing here is
about any one form; `<FORM>` is whichever form the PRD migrates.

## 1. The gate (before any slicing)

The **disposition ledger is a hard gate** (ADR-0015) — it extends "nothing
dropped silently" below construct grain, to the grain where silent losses
happen (a default, an editability mode, a `:GLOBAL` contract):

```bash
python3 .agents/skills/forms-xml-analyzer/scripts/disposition_ledger.py \
    docs/generated/<FORM>.spec.json --merge docs/generated/<FORM>.ledger.json \
    --out docs/generated/<FORM>.ledger.json --md docs/generated/<FORM>.ledger.md \
    --check --check-refs
```

- **If `--check` fails, stop.** Work the unassigned rows with the user — each
  must become `migrated-as`, `re-decided` (with `reason` + `decidedBy`),
  `deferred` (with a tracking ref), or `obsolete` (with the rule id). Never
  assign a disposition for the user on a judgment row; propose, then record.
- **If `--check-refs` fails, fix the rot first**: a dead `#N`, a `deferred` row
  without a ref, or a `deferred` ref that is closed (deliver the row — flip it
  to `migrated-as` — or re-defer it).

## 2. Break down and publish via to-issues

Follow the **`to-issues`** skill process as written (gather context, explore,
draft vertical slices, quiz the user, publish with its issue template). Layer
these requirements onto every build ticket:

1. **`Covers:` line.** Each ticket lists the ledger-row ids it delivers. A row
   no ticket covers and no other disposition explains is a leak the gate exists
   to catch.
2. **Field fidelity table.** A ticket that touches items embeds, per item, the
   spec's decision-prone facts so the builder implements against a matrix, not
   prose: default (`initializeValue`), editability mode (editable / read-only /
   **insert-only** when `updateAllowed=false, insertAllowed=true` — the
   `forms-insert-only-field` rule), required, format mask, visibility,
   `copyValueFromItem`. Pull the values from the spec — never from memory.
3. **No unverified absence claims.** A ticket (and the code built from it) may
   claim "Forms has no X" only with a spec citation (`<FORM>.spec.json` — the
   element or its absence is checkable). `migration-review` verifies these
   claims; an uncited absence claim is a review finding.
4. **Acceptance criteria come from ledger rows.** Every `migrated-as` row a
   ticket covers — property rows especially — becomes an acceptance criterion
   asserting the named behaviour at runtime, per the UI-test convention
   (behavioural parity + layout fidelity). An `initializeValue` row is
   delivered by "a new record defaults the field to that value"; an insert-only
   editability row by "the field accepts input on a new record and is read-only
   on a persisted one". The ledger proves *decided*; the criteria make the
   build prove *delivered*.

## Relationship to the rest of the method

- **`forms-xml-analyzer`** produces the spec and ships the ledger script.
- **`migration-review`** re-runs the same gate at review time and emits the
  HITL tickets (ADR-0014); it treats a `migrated-as` row no test asserts as a
  careful-review item.
- **ADR-0015** records the grain and gate-point trade-offs; the glossary terms
  (_Disposition_, _Re-decision_, _Cross-form contract_) live in `CONTEXT.md`.
