# Migrating a form — a developer's guide

How to migrate **one** Oracle Forms form to Vaadin with this repo's method, from the
`.fmb` on disk to a reviewed, implemented Vaadin view.

This guide is the **map**; the authority for each step is the skill it names. Run the
steps in order — each consumes the previous one's output. For the *why* behind the
method read [`PLAN.md`](../PLAN.md); for the vocabulary read [`CONTEXT.md`](../CONTEXT.md).

Throughout, `<FORM>` is the form you are migrating. The worked example is `ORDERS`
(`forms-summit-example/ORDERS.fmb`).

---

## The flow

```
.fmb ─(0) frmf2xml─▶ <FORM>_fmb.xml
        │
        ▼ (1) forms-xml-analyzer          unopinionated structural spec
   <FORM>.spec.json
        │
        ▼ (2) to-prd                       Vaadin PRD (architecture decisions enter here)
   PRD on the issue tracker
        │
        ▼ (3) migration-to-issues          split into build tickets  [ledger gate]
   ready-for-agent issues
        │
        ▼ (4) implementation               build the code — can be AFK
   Vaadin view + tests
        │
        ▼ (5) migration-review             human sign-off (ready-for-human tickets)
   parity confirmed → done
```

**Steps 1 → 3 are unopinionated → opinionated.** The analyzer records only *what the
form is*. The PRD is where *what it should become* is decided — that is where Vaadin
and your chosen architecture enter. `migration-to-issues` then turns that into
buildable work. Skills live under `.agents/skills/`.

**The flow also runs backwards.** Steps 4 and 5 don't just consume the process —
they *improve* it. Every correction and every review finding is sorted
generalizable-vs-specific; the generalizable ones flow back into the reusable IP
(`migration-rules.yaml`, the skills, the ADRs) so the *next* form starts smarter.
That loop is the point of the POC, not a side effect — see
[Continuous improvement](#continuous-improvement--the-method-gets-smarter-each-form)
below.

---

## Step 0 — Convert the `.fmb` to XML

The whole toolchain reads the `frmf2xml` XML export, not the binary `.fmb`.

```bash
frmf2xml <FORM>.fmb        # Oracle Forms tool → <FORM>_fmb.xml
```

**Already done in this repository** — the Summit exports are committed (e.g.
`forms-summit-example/ORDERS_fmb.xml`). You only run `frmf2xml` yourself when bringing
in a new form from outside the repo.

---

## Step 1 — Analyze the form → unopinionated spec

**Skill:** [`forms-xml-analyzer`](../.agents/skills/forms-xml-analyzer/SKILL.md)

**Purpose.** Turn the XML into a faithful, **decision-free** structural spec at
form-object grain — every block, item, trigger, relation, LOV, record group, alert,
program unit, and canvas, plus the Forms defaults it injects and the derived signals
the PRD step needs. It records *what the form is*, never what it should *become*: a
Formula item's expression is captured verbatim, no Vaadin target is chosen. This is
the neutral contract every later step reads, and keeping it opinion-free is what lets
the method later sort each migration decision as generalizable or specific.

**Run it.** Point the skill at the form's XML (`forms-summit-example/<FORM>_fmb.xml`).
It writes two artifacts to `docs/generated/`: `<FORM>.spec.json` (machine-readable,
feeds every later step) and `<FORM>.spec.md` (human-readable digest).

**Completeness guarantee.** The spec carries a `census` (tag → count) so nothing slips
silently — the schema and derived-signal heuristics are documented in the skill's
[`REFERENCE.md`](../.agents/skills/forms-xml-analyzer/REFERENCE.md).

**Watch for `dependencies[]`.** A form rarely stands alone — it may reference a menu
module, attached PL/SQL libraries (e.g. `calendar.pll`), sibling forms called via
`CALL_FORM`/`OPEN_FORM`, or cross-form `:GLOBAL`/`:PARAMETER` contracts. The analyzer
inventories each as `status: "undecided"` and decides nothing. Before treating any as
out of scope, decide **per dependency, with the user**: *spec it* (run the analyzer on
that module too) or *ignore it* (an explicit, acknowledged exclusion). Never drop one
silently. In-scope-but-deferred dependencies resurface as tracked work in Step 5.

> If the analyzer errors on the form, **that is itself a finding** — capture the XML
> shape; do not hand-edit the spec to paper over it.

---

## Step 2 — Generate the PRD → the Vaadin/architecture decisions

**Skill:** [`to-prd`](../.agents/skills/to-prd/SKILL.md)

**Purpose.** Synthesise the spec (plus your architecture) into a PRD published to the
issue tracker. **This is the opinionated step** — the analyzer said what the form *is*;
the PRD says what it *becomes* in Vaadin, and that answer depends on the architecture
you have chosen. The decisions come from two places:

- **The mapping ruleset** [`docs/migration-rules.yaml`](migration-rules.yaml) — the
  Forms-construct → Vaadin-target decision tree (table trigger → stays in the DB;
  Execute Query → filter bar; LOV → LookupField; pessimistic lock → `ORA_ROWSCN`;
  frame → grouping Section; …). It is the reusable IP; you *extend* it as you learn
  (Step 4), you don't fork it per form.
- **The ADRs** in [`docs/adr/`](adr/) — the architecture the PRD writes against:
  business logic in the domain layer (ADR-0001), Binder not Signals (ADR-0002), Aura
  owns visual styling (ADR-0008), structural layout fidelity (ADR-0012), read-only is
  a state not a type (ADR-0013).

Run `to-prd`. It writes the PRD against the spec, the ruleset, and the ADRs, and
publishes it `ready-for-agent`. The worked example is
[`docs/prd-orders-view.md`](prd-orders-view.md) (and its `-v3` revision).

> A different target architecture (a different ruleset, different ADRs) produces a
> different PRD from the *same* spec — which is exactly why the spec stays neutral.

---

## Step 3 — Split into issues → the build backlog

**Skill:** [`migration-to-issues`](../.agents/skills/migration-to-issues/SKILL.md)
(wraps the generic `to-issues`)

**Purpose.** Turn the PRD into independently-buildable **construct-grain** tickets
(~7 for ORDERS: master block, detail block, filter bar, LOV/LookupField, trigger
totals, locking model, kept/dissolved DB objects). Clustering to construct grain *is*
the architectural-decision step — it avoids a wall of forty "migrate KEY-NEXT-ITEM"
non-tickets and keeps each ticket reviewable and testable on its own.

**⚠ The disposition-ledger gate runs first (ADR-0015).** Before it cuts a single
ticket, this skill runs the ledger — one row per form object *plus* per decision-prone
mapped property (a default, an editability mode, a `:GLOBAL` contract), the grain
where silent losses actually happen. Every row must end in exactly one disposition
(`migrated-as` / `re-decided` / `deferred` / `obsolete`). **If the gate fails,
ticket-cutting stops** until each unassigned row is worked *with the user*. The ledger
is written to `docs/generated/<FORM>.ledger.{json,md}` and carries prior decisions
forward across spec regenerations, so it is safe to refresh.

Once the gate passes, each build ticket carries a `Covers:` line (the ledger rows it
delivers), a field-fidelity table (default / editability / required / format mask,
pulled from the spec not memory), and **acceptance criteria derived from ledger rows**
— so the build has to *prove delivered*, not just claim decided.

---

## Step 4 — Implementation (can be AFK)

**Skills:** the generic Vaadin set — `forms-and-validation`, `data-providers`,
`views-and-navigation`, `testbench-testing`, … — driven from each `ready-for-agent`
ticket. Because the tickets are self-contained (fidelity table + acceptance criteria),
an agent can build them **unattended (AFK)**.

Build through the layered architecture (ADR-0001, ADR-0002):

```
View  ◀  Presenter  ◀  Service  ◀  Domain  ◀  (JdbcClient)  ◀  Data
```

- **Domain** owns the *formula* (`getExtendedPrice()`, `recalculateTotal()`) — pure,
  unit-tested; where `WHEN-VALIDATE-ITEM` PL/SQL lands.
- **Presenter** owns *orchestration* (Grid Pro cell-commit → call the domain method →
  push the new total).
- **Service** owns *load and commit* (fetch the graph; flush transactionally under the
  `ORA_ROWSCN` optimistic-lock check — the Forms `COMMIT_FORM` analogue).
- **Data** = Spring `JdbcClient`.

Build the **riskiest construct first** (for ORDERS: master-detail + totals, and the
LookupField lazy-paging go/no-go) while a rebuild is still cheap. As you correct
generated code, **sort every fix** generalizable-vs-specific and feed the
generalizable ones back into the ruleset — this is the improvement loop, described
in full under [Continuous improvement](#continuous-improvement--the-method-gets-smarter-each-form).

---

## Step 5 — Migration review → sign-off

**Skill:** [`migration-review`](../.agents/skills/migration-review/SKILL.md)

**Purpose.** Emit the work a **human must own** as first-class `ready-for-human`
tickets (each opens `> *This was generated by AI during triage.*`) — because an agent
can make code compile and pass unit tests, but it cannot certify that the screen
*behaves* and *reads* like Forms. See [ADR-0014](adr/0014-hitl-review-tickets.md). Run
it after the build tickets are drafted, or per construct right after it is built while
the seams are fresh. Three ticket types:

1. **Careful-review / manual-test** — behavioural-parity + layout-fidelity checks a
   human drives **side-by-side against live Forms** (documented seams, the
   ADR-0012/0013 layout gate, `alerts[]` business rules, and **absence-claim**
   verification — any "Forms has no X" in the code checked against the spec).
2. **Out-of-slice / deferred follow-up** — in-scope-but-deferred dependencies become
   tracked issues/PRDs; the human makes the **defer-vs-reject** call (reject →
   `.out-of-scope/`).
3. **Generalization-review** — generic Forms affordances the ruleset baked into one
   form's view (record actions, filter bar, `ORA_ROWSCN` path) recommended for a
   **reusable layer**.

**The ledger gate runs again** here, now that the build has happened and truth may
have drifted from the decisions. A `migrated-as` row **no test asserts** becomes a
careful-review item — the disposition proves *decided*, only an assertion proves
*delivered*, and property rows are the ones that slip. A build that deliberately
deviated from a row's spec value must have flipped that row to `re-decided` (with
`reason` + `decidedBy`).

**Done** when the construct compiles, shows behavioural parity on the spine flow
side-by-side against live Forms, every covered ledger row is asserted by a test, and
the careful-review ticket is worked. The form is done when every construct is done.

---

## Continuous improvement — the method gets smarter each form

The steps above migrate *one* form. This section is the cross-cutting discipline
that makes migrating the *next* one cheaper: **every correction and every review
finding is sorted, and the reusable half is pushed back into the tooling.** It is
not a phase — it runs throughout Steps 4 and 5. This sort *is* the finding the POC
exists to report; the rationale lives in [`PLAN.md`](../PLAN.md#the-finding-what-this-poc-actually-argues).

**The sort.** Every fix is exactly one of two things:

- **Generalizable** — a rule the tool *should* have known. It earns this status
  only when the rule can be **stated in form-construct terms** (e.g. "any
  table-level audit trigger stays in the DB"). Push it into the reusable IP and
  regenerate.
- **Specific** — an irreducible quirk of *this* form. Hand-fix it, log it, move on.

A fix is **specific by default**: if you can't articulate the general rule, it does
not go into the tooling. That conservatism is what keeps the ruleset reusable
instead of secretly overfit to one form.

**Where a generalizable fix lands** (don't fork per form — *extend* the shared asset):

| You learned… | Push it into… |
|---|---|
| A Forms-construct → Vaadin-target rule the PRD got wrong | [`migration-rules.yaml`](migration-rules.yaml), then regenerate the PRD |
| A build habit an agent kept getting wrong | the relevant Vaadin skill under `.agents/skills/` |
| An architecture decision that should bind every form | a new/amended ADR in [`docs/adr/`](adr/) |
| A generic Forms affordance baked into one view (record actions, filter bar, `ORA_ROWSCN` path) | a **generalization-review** ticket (Step 5) recommending a reusable layer |

**The three entry points where the loop fires:**

1. **Correcting generated code (Step 4)** — the *reactive* entry point. You fixed
   something the agent produced; sort the fix before moving on.
2. **Generalization-review tickets (Step 5, type 3)** — the *proactive* entry
   point. Before you even hit a bug, the review flags an affordance the ruleset
   solved for one form that belongs in a reusable layer.
3. **A `re-decided` ledger row** — when a build deliberately deviates from a spec
   value, the ledger row flips to `re-decided` with `reason` + `decidedBy`. If that
   re-decision is generalizable, it is also a ruleset change, not just a one-off.

**The number this produces.** The **ratio of generalizable-to-specific fixes** is
the single metric the audience cares about — it is their automation ceiling. Keeping
the migration log sorted live (not reconstructed afterward) is what makes that ratio
trustworthy. Log the fix *when you make it*, sorted, or the finding is lost.

---

## Reference map

| Step | Skill / artifact |
|---|---|
| 0 Convert | Oracle `frmf2xml` (exports already committed) |
| 1 Analyze | [`forms-xml-analyzer`](../.agents/skills/forms-xml-analyzer/SKILL.md) + [`REFERENCE.md`](../.agents/skills/forms-xml-analyzer/REFERENCE.md) |
| 2 PRD | [`to-prd`](../.agents/skills/to-prd/SKILL.md); decisions from [`migration-rules.yaml`](migration-rules.yaml) + [`docs/adr/`](adr/) |
| 3 Issues | [`migration-to-issues`](../.agents/skills/migration-to-issues/SKILL.md); ledger gate = [ADR-0015](adr/0015-disposition-ledger-gates-ticket-generation.md) |
| 4 Implementation | the Vaadin skills; layering per ADR-0001/0002/0008/0012/0013 |
| 5 Review | [`migration-review`](../.agents/skills/migration-review/SKILL.md); [ADR-0014](adr/0014-hitl-review-tickets.md) |
| ↩ Improve | the generalizable-vs-specific sort → [`migration-rules.yaml`](migration-rules.yaml), the skills, [`docs/adr/`](adr/); ratio = automation ceiling |
| Method & vocabulary | [`PLAN.md`](../PLAN.md), [`CONTEXT.md`](../CONTEXT.md) |
| Worked example (ORDERS) | `docs/generated/ORDERS.*`, `docs/orders-form-spec.md`, `docs/prd-orders-view*.md` |
