# The forms-migration process — one form, end to end

How to migrate **one** Oracle Forms form to Vaadin with this repo's method, from
picking the `.fmb` to declaring the migration done.

This is a **runbook that assembles the existing process** — it does not invent or
change it. The authority for each stage is the skill or doc cited beside it; when
they disagree, they win. For the *why* behind the method (the "sorting problem"
thesis, the two-week POC sequence) read [`PLAN.md`](../PLAN.md); for the
vocabulary read [`CONTEXT.md`](../CONTEXT.md).

Throughout, `<FORM>` is whichever form you are migrating. The worked example is
`ORDERS` (`forms-summit-example/ORDERS.fmb`).

---

## The pipeline at a glance

```
.fmb  ──frmf2xml──▶  <FORM>_fmb.xml
   │
   ▼  (1) forms-xml-analyzer: analyze_form.py
<FORM>.spec.json  (decision-free structural spec, form-object grain)
   │
   ├─▶ (2) resolve dependencies[]  — ask per surface: spec it / ignore it
   │
   ├─▶ (3) apply migration-rules.yaml  — construct → Vaadin target
   │        check_against_ruleset.py   (spec ⇄ ruleset contract holds)
   │
   ▼  (4) disposition_ledger.py --check --check-refs   ◀── HARD GATE (ADR-0015)
<FORM>.ledger.json  (every form object + decision-prone property has a disposition)
   │
   ▼  (5) migration-to-issues (wraps to-issues)   +   to-prd
GitHub build tickets (ready-for-agent, construct grain) + PRD
   │
   ▼  (6) build loop, per construct, riskiest first
Vaadin code through View ◀ Presenter ◀ Service ◀ Domain ◀ Data  + UI tests
   │
   ▼  (7) migration-review  — HITL tickets (ready-for-human) + ledger re-check
   │
   ▼  (8) done gate: compiles + behavioural parity on the spine flow
scorecard row filled
```

Three migration skills glue it together, in order:
[`forms-xml-analyzer`](../.agents/skills/forms-xml-analyzer/SKILL.md) →
[`migration-to-issues`](../.agents/skills/migration-to-issues/SKILL.md) →
[`migration-review`](../.agents/skills/migration-review/SKILL.md). The build in
between uses the generic Vaadin skills (`forms-and-validation`, `data-providers`,
`testbench-testing`, …). Skills live under `.agents/skills/`.

---

## Prerequisites

- The form's **FMB XML** — the `frmf2xml` export of the `.fmb`. In this repo the
  Summit exports are already committed (e.g. `forms-summit-example/ORDERS_fmb.xml`).
  For a new form, run Oracle's `frmf2xml` on its `.fmb` to produce `<FORM>_fmb.xml`.
- The **mapping ruleset** [`docs/migration-rules.yaml`](migration-rules.yaml) — the
  Forms-construct → Vaadin-target decision tree. Frozen IP; you *extend* it as you
  learn (stage 6), you don't fork it per form.
- The **runnable scaffold** — the layered Vaadin/Spring/Oracle app that code lands
  in. See [`README.md`](../README.md) to bring up the DB and run it.
- The **live Forms app** as the behavioural oracle for parity checks (stages 6–7).
- `python3` (3.9+, stdlib only) and, for `--check-refs`, the `gh` CLI.

---

## Stage 0 — Pick the form

Choose the form and locate its FMB XML. If you only have the `.fmb`, export it:

```bash
frmf2xml <FORM>.fmb        # Oracle tool → <FORM>_fmb.xml
```

Migrate one form at a time. A form that references other forms/libraries does not
pull them in automatically — those surface as `dependencies[]` in stage 2 and are
decided explicitly, never dragged along silently.

---

## Stage 1 — Analyze → structural spec

Run the analyzer. It is **mechanical and decision-free**: it records what the form
*is* (every block, item, trigger, relation, LOV, record group, alert, program
unit, canvas — plus Forms defaults it injects and derived signals the ruleset
needs), never what it should *become*.

```bash
python3 .agents/skills/forms-xml-analyzer/scripts/analyze_form.py \
    forms-summit-example/<FORM>_fmb.xml \
    --out docs/generated/<FORM>.spec.json \
    --md  docs/generated/<FORM>.spec.md
```

- `<FORM>.spec.json` — machine-readable spec that the ruleset, ledger, and planner
  read. This is the contract for every downstream step.
- `<FORM>.spec.md` — human-readable digest.

**Completeness guarantee:** the spec is at *form-object grain* and carries a
`census` (tag → count) so nothing slips silently. Full output schema and the
derived-signal heuristics are in
[`forms-xml-analyzer/REFERENCE.md`](../.agents/skills/forms-xml-analyzer/REFERENCE.md).

> If the analyzer errors on the form, **that is itself a finding** — capture the
> XML shape; do not hand-edit the spec to paper over it.

---

## Stage 2 — Resolve dependencies (ask, never silently drop)

The analyzer inventories every external migration unit into `dependencies[]` — menu
module, attached PL/SQL libraries (e.g. `calendar.pll`), sibling forms called via
`CALL_FORM`/`OPEN_FORM`/`NEW_FORM`/`RUN_PRODUCT`, object-library subclassing, and
**cross-form contracts** (`:GLOBAL.*` / `:PARAMETER.*` shared state) — each flagged
`status: "undecided"`. The analyzer makes **no** scoping decision.

**Mandatory gate:** before treating any dependency as out of scope, stop and ask the
user, *per dependency*:

- **spec it** — run the analyzer on that module too and record it as a dependency
  (set the entry's `status: "spec"`); or
- **ignore it** — an explicit, acknowledged exclusion (`status: "ignore"`).

Never pick for the user; record each answer back onto the entry so the choice is
auditable in the spec. (Deferred-but-in-scope surfaces become tracked follow-up
work in stage 7 — distinct from the `.out-of-scope/` *rejection* lane.)

---

## Stage 3 — Map via the ruleset

The spec has no Vaadin knowledge; [`migration-rules.yaml`](migration-rules.yaml)
supplies the decisions (table trigger → stays in DB; Execute Query → filter bar;
LOV → LookupField; pessimistic lock → `ORA_ROWSCN`; frame → grouping Section; …).
It maps **1:1 to the scorecard rows** and is the substrate of the
generalizable-vs-specific sort.

Verify the spec actually carries the fields that make the rules fire, and that
every referenced rule id exists:

```bash
python3 .agents/skills/forms-xml-analyzer/scripts/check_against_ruleset.py \
    docs/generated/<FORM>.spec.json docs/migration-rules.yaml
# exit 0 = all contracts hold; 1 = at least one failed
```

---

## Stage 4 — The disposition-ledger gate (ADR-0015)

**Hard gate before any tickets.** The ledger extends "nothing dropped silently"
below construct grain — down to the grain where silent losses actually happen (a
default, an editability mode, a `:GLOBAL` contract). It is one row per form object
plus per decision-prone mapped property; every row must end in exactly one
disposition: `migrated-as`, `re-decided` (with `reason` + `decidedBy`), `deferred`
(with a tracking ref), or `obsolete` (with the rule id).

```bash
python3 .agents/skills/forms-xml-analyzer/scripts/disposition_ledger.py \
    docs/generated/<FORM>.spec.json \
    --merge docs/generated/<FORM>.ledger.json \
    --out   docs/generated/<FORM>.ledger.json \
    --md    docs/generated/<FORM>.ledger.md \
    --check --check-refs
```

- `--merge` carries prior decisions forward across spec regenerations (safe to
  refresh).
- **If `--check` fails, stop.** Work each unassigned row *with the user* until it
  has a disposition. Never assign a disposition for the user on a judgment row —
  propose, then record.
- **If `--check-refs` fails, fix the rot first** (needs `gh`): a dead `#N`, a
  `deferred` row without a ref, or a `deferred` ref that is already closed (deliver
  the row and flip it to `migrated-as`, or re-defer it).

Mechanical carries (maximumLength, prompts, field order) get *no* ledger rows —
the layout-fidelity gate (ADR-0012) verifies those at runtime instead.

---

## Stage 5 — PRD and build tickets

Use [`migration-to-issues`](../.agents/skills/migration-to-issues/SKILL.md) (it
re-runs the stage-4 gate, then wraps the generic `to-issues` skill). Optionally
capture the whole slice as a PRD first with `to-prd`.

Tickets are at **construct grain** (~7 for ORDERS: master block, detail block,
filter bar, LOV/LookupField, trigger totals, locking model, kept/dissolved DB
objects) — the clustering step *is* the architectural-decision step. Each build
ticket is `ready-for-agent` and carries the migration-specific requirements:

1. **`Covers:` line** — the ledger-row ids the ticket delivers. A row no ticket
   covers and no other disposition explains is a leak.
2. **Field-fidelity table** — per item, the decision-prone facts (default /
   editability mode incl. insert-only / required / format mask / visibility /
   `copyValueFromItem`), pulled from the spec, never from memory.
3. **No unverified absence claims** — "Forms has no X" only with a spec citation.
4. **Acceptance criteria from ledger rows** — every `migrated-as` row becomes a
   runtime assertion (an `initializeValue` row → "a new record defaults the field
   to that value"; an insert-only row → "accepts input on a new record, read-only
   on a persisted one"). The ledger proves *decided*; the criteria make the build
   prove *delivered*.

Transport is GitHub Issues, but the markdown bodies are authored by the planner so
the content ports to Jira/GitLab with a different `create` call.

---

## Stage 6 — Build each construct (the build loop)

Per **construct**, riskiest first (for ORDERS: master-detail block + totals, and
the LookupField lazy-paging go/no-go — while a full rebuild is still cheap).

Build through the layered architecture — see
[ADR-0001](adr/0001-business-logic-in-domain-layer.md),
[ADR-0002](adr/0002-binder-validation-no-signals.md):

```
View  ◀  Presenter  ◀  Service  ◀  Domain  ◀  (JdbcClient)  ◀  Data
```

- **Domain** owns the *formula* (`getExtendedPrice()`, `recalculateTotal()`) — pure,
  unit-tested. This is where `WHEN-VALIDATE-ITEM` PL/SQL lands.
- **Presenter** owns *orchestration* (Grid Pro cell-commit → call the domain method
  → push the new total).
- **Service** owns *load and commit* (fetch the graph; flush transactionally under
  the `ORA_ROWSCN` check — the Forms `COMMIT_FORM` analogue).
- **Data** = Spring `JdbcClient`.

Use the generic Vaadin skills for the actual code (`forms-and-validation`,
`data-providers`, `views-and-navigation`, `testbench-testing`, …).

**The loop (regenerate → validate → sort):**

1. **Generate** the construct from its ticket.
2. **Validate** against the live Forms app and **diagnose which layer failed**:
   ticket wrong → fix the **ruleset**; code wrong but ticket right → fix the
   **execution skill/prompt**; irreducible form quirk → **hand-fix, touch no skill**.
3. **Sort every fix**: *generalizable* → push into the ruleset/skill as the new
   behaviour (per the "encode decisions in the YAML" convention) and regenerate;
   *specific* → stay manual, log in the scorecard.
4. **Stopping gate:** compiles **+ behavioural parity on the spine flow**, checked
   side-by-side against live Forms. Cap refinement at **~2 passes per construct** —
   if it won't converge, **stop and write it up**; non-convergence is a finding, not
   a reason to grind.

The **spine flow** that must run (ORDERS): load an order → edit line quantities →
swap a product via LookupField (re-default price + description) → add/delete lines →
totals recompute live → commit under the `ORA_ROWSCN` check.

> **The one discipline that can sink this:** the migration log / scorecard is
> instrumentation you run from **day one**, live and sorted — not documentation
> written at the end (see `PLAN.md`).

---

## Stage 7 — Review (HITL) and close the ledger loop

Run [`migration-review`](../.agents/skills/migration-review/SKILL.md) after the
build tickets are drafted, or per construct right after it is built while the seams
are fresh. It emits three kinds of **`ready-for-human`** tickets (each opens with
`> *This was generated by AI during triage.*`) — see
[ADR-0014](adr/0014-hitl-review-tickets.md):

1. **Careful-review / manual-test** — behavioural-parity + layout-fidelity checks a
   human drives side-by-side against live Forms (documented seams, the ADR-0012/0013
   layout gate, `Refactor`/`Unsupported`/`escalate` ruleset outcomes, `alerts[]`
   business rules, and **absence-claim** verification against the spec).
2. **Out-of-slice / deferred follow-up** — in-scope-but-deferred dependencies become
   tracked issues/PRDs. The human makes the **defer-vs-reject** call per surface
   (reject → `.out-of-scope/`, per `triage/OUT-OF-SCOPE.md`).
3. **Generalization-review** — generic Forms affordances the ruleset baked into a
   form's view (record actions, filter bar, `ORA_ROWSCN` path, `CLIENT_IDENTIFIER`
   bridge, confirm-on-destructive) recommended for a **reusable layer**. A candidate
   earns "generalize" only if the rule can be stated in form-construct terms;
   otherwise specific by default.

**Re-run the ledger gate** now that the build has happened and truth may have
drifted:

```bash
python3 .agents/skills/forms-xml-analyzer/scripts/disposition_ledger.py \
    docs/generated/<FORM>.spec.json \
    --merge docs/generated/<FORM>.ledger.json \
    --out   docs/generated/<FORM>.ledger.json \
    --md    docs/generated/<FORM>.ledger.md \
    --check --check-refs
```

- A row still unassigned, or a `migrated-as` no ticket/PR actually delivered →
  careful-review item.
- A `migrated-as` row **no test asserts** → careful-review item (decided ≠
  delivered; property rows are the ones that slip).
- A build that **deliberately deviated** from a row's spec value must have flipped
  that row to `re-decided` (`reason` + `decidedBy`); an undocumented deviation found
  here is recorded there before the review closes. Generalizable re-decisions are
  additionally encoded into `migration-rules.yaml`.

---

## Stage 8 — Done

A construct is done when it **compiles + shows behavioural parity on the spine
flow**, verified side-by-side against live Forms, and:

- every ledger row it covers has a disposition **and** (for `migrated-as`) a UI test
  asserting it;
- its careful-review ticket is worked;
- deferred surfaces are tracked (issue/PRD), not silently dropped;
- its **scorecard row** is filled — Construct / Vaadin target / AI-did-unaided /
  needed-hand-fixing (split generalized-vs-specific) / genuinely-hard /
  behavioural-parity / verdict (`Clean` / `Assisted` / `Manual` / `Stays-in-DB`).

The form is done when every construct row is done. The **ratio** of
generalized-into-skill vs stayed-specific fixes is the number the POC exists to
report — the reader's automation ceiling.

---

## Reference map

| Stage | Authority |
|---|---|
| Overview, build loop, thesis, schedule | [`PLAN.md`](../PLAN.md) |
| Vocabulary (construct, disposition, layout fidelity, layers) | [`CONTEXT.md`](../CONTEXT.md) |
| 1–2 Analyze / dependencies | [`forms-xml-analyzer/SKILL.md`](../.agents/skills/forms-xml-analyzer/SKILL.md), [`REFERENCE.md`](../.agents/skills/forms-xml-analyzer/REFERENCE.md) |
| 3 Mapping | [`docs/migration-rules.yaml`](migration-rules.yaml) |
| 4, 7 Disposition-ledger gate | `disposition_ledger.py`, [ADR-0015](adr/0015-disposition-ledger-gates-ticket-generation.md) |
| 5 Tickets | [`migration-to-issues/SKILL.md`](../.agents/skills/migration-to-issues/SKILL.md) (wraps `to-issues`) |
| 6 Build | ADR-0001/0002/0008/0012/0013, the Vaadin skills |
| 7 Review (HITL) | [`migration-review/SKILL.md`](../.agents/skills/migration-review/SKILL.md), [ADR-0014](adr/0014-hitl-review-tickets.md) |
| Worked example (ORDERS) | `docs/generated/ORDERS.*`, `docs/orders-form-spec.md`, `docs/prd-orders-view*.md` |
