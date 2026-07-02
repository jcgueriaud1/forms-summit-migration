# Migrating Oracle Forms to Vaadin with AI: what actually generalizes

> **The question:** can an Oracle Forms master-detail block — an order with its
> line items, trigger-driven totals, an LOV-backed product lookup, optimistic
> locking — become a Vaadin Grid-and-form? And, more usefully: *where does it
> break, and which of those breaks can a tool learn so it never breaks again?*

This is the write-up of a time-boxed proof of concept. We took one representative
slice of the Oracle **Summit** demo schema — the `ORDERS` form, `S_ORD` master /
`S_ITEM` detail — and migrated it to **Vaadin 25 + Spring Boot 4**, with the
migration itself driven by a set of composable AI skills. The app runs against a
real Oracle database in a Docker container, and every migrated construct ships
with a browser test that drives it end-to-end.

The migration is the vehicle. The finding is the payoff.

---

## The finding: AI-assisted modernization is a sorting problem

Here is the thesis the whole POC exists to argue.

Every correction you make to AI-generated code is one of exactly two things:

- **Generalizable** — a rule the tool *should* have known. You push it back into
  the mapping ruleset, and it now works for **every** form, forever.
- **Specific** — an irreducible quirk of *this* form. You hand-fix it, log it, and
  move on. It teaches the tool nothing.

The reusable asset is the accumulated *generalizable* set. It gets smarter every
time a fix is sorted correctly. And the single number a Forms-shop architect
actually cares about is the **ratio** of generalizable-to-specific fixes — because
that ratio *is* their automation ceiling.

One rule keeps this honest: **a fix is "specific" by default.** It only earns
"generalizable" status when the rule behind it can be stated in *form-construct
terms* — "any table-level audit trigger stays in the database", not "the ORDERS
header needed an extra field". If you can't articulate the rule generically, you
log it as specific. That conservatism is exactly what separates a reusable skill
from one quietly overfit to Summit.

---

## Why this is hard (and why a naive port misses it)

An Oracle Forms `.fmb` is not a UI layout. It is a dense bundle of:

- **blocks** bound to base tables, with master-detail **relations** that keep a
  header and its lines navigating together;
- **triggers** (the ORDERS form has 45 of them) carrying validation, defaulting,
  query and commit logic in PL/SQL;
- **items** whose *type* carries meaning — a Radio Group says "small fixed
  choice", an LOV says "lookup", a Display Item says "computed, read-only";
- **frames and boilerplate text** that group fields and title sections;
- **LOVs and record groups** backing type-ahead lookups;
- a **locking and commit model** (`COMMIT_FORM`) that flushes the whole
  master-detail graph as one unit.

A migration that only looks at the screen drops most of this silently. The
interesting work is deciding, construct by construct, what each of these *becomes*
on the web — and being honest about what doesn't survive the trip.

---

## The architecture we landed on

We routed every datum through a strict, testable layering:

```
View  ←  Presenter  ←  Service  ←  Domain  ←  (JdbcClient)  ←  Data
```

- **Domain** owns the *formula* — pure, unit-testable computation. The order
  total is `Order.total()`, a `Sum` over each line's `itemTotal()`; the per-order
  next line id is `Order.nextItemId()` (the Forms `PRE-INSERT` rule
  `NVL(MAX(item_id),0)+1`). This is where declarative Forms calculations and
  `WHEN-VALIDATE-*` logic land. ([ADR-0001](adr/0001-business-logic-in-domain-layer.md))
- **Presenter** owns *orchestration* — it listens for grid edits, mutates the
  in-memory bean, calls the domain formula, and pushes the new total to the view.
  It *calls* formulas; it never *contains* them.
- **Service** owns *load and commit* — it fetches the whole object graph in one
  path and flushes it transactionally, mirroring a Forms `COMMIT`.
- **Data** is Spring's `JdbcClient` (chosen over jOOQ to sidestep Oracle-dialect
  licensing).

A few decisions are worth calling out because they are where "faithful migration"
and "good web app" pull against each other.

### The three headline constructs

| Forms construct | Vaadin target | The honest seam |
|---|---|---|
| **Execute Query / Enter Query** (F7/F8) | A persistent **filter bar** + a **lazy, paged** data provider over `S_ORD` | We *replace* the modal query mode rather than replicate it. We give up arbitrary QBE expressiveness for a curated filter set — but it demonstrably pages against the DB, never loads the whole table. |
| **Product LOV** | A **type-ahead lookup** backed by the LOV's record-group query | Near-direct analogue of the LOV idiom. |
| **Master-detail block** | Master = single-select Grid with deselect disabled ("always a current record"); detail = **inline-editable** line-items grid | Grid commits per cell; Forms validates per item on navigation. A documentable difference, not a bug. |

### Transaction and locking: "we did not touch your database"

We commit the master + detail graph as **one transaction**, and we do optimistic
locking with **`ORA_ROWSCN`** — read on load, re-check in
`UPDATE … WHERE id = ? AND ORA_ROWSCN = ?`; zero rows updated means a conflict, so
roll back. **No schema change.** That is the credibility claim a DBA will test
first, and it holds. (We document the `ROWDEPENDENCIES` block-vs-row SCN-precision
caveat as an honest finding rather than hiding it.)

### Some things you don't migrate — and they keep working

The boundary rule: **table-level / data-integrity logic stays in the database;
form-serving logic moves to the Java service layer; logic the new architecture
absorbs simply dissolves.**

- The **`AUDIT_S_ITEM` trigger** (`AFTER INSERT`) **stays in the DB**, untouched.
  It fires transparently inside the Vaadin commit's transaction — zero Java
  written. The one bridge: we set Oracle's `CLIENT_IDENTIFIER` per request from
  the Spring Security principal (~5 lines), so the trigger keeps recording the
  *real human* despite a pooled service account.
- The **`get_product_image` function** **dissolves** into the product lookup
  query — the data provider already fetches the row, so the filename just comes
  along.

### Styling is dropped; structure is not

This is the subtlest line we drew, and we got it wrong once before getting it
right — which is exactly the kind of thing this POC exists to surface.

[ADR-0008](adr/0008-aura-theme-owns-visual-styling.md) made the **Aura theme** the
single owner of visual styling. Forms' per-field fonts, colours, bevels and
visual-attribute groups are **dropped, not translated**. Migrated screens are not
pixel-identical to Forms, on purpose.

But our first pass over-reached: it dropped field *geometry* wholesale — and
geometry encodes **structure**, not just pixels. The migrated ORDERS header came
out with its fields reordered, its bordered "Order Information" group gone, its
section title missing, and its `PAYMENT_TYPE` radio flattened to a text field. The
spec census still cheerfully read "183 elements, nothing dropped."

That gap — *counted ≠ rendered* — produced two corrections, both now first-class
rules:

- **[ADR-0012](adr/0012-structural-layout-fidelity.md): structural layout fidelity
  is migrated.** Field order, frame grouping, and section titles are preserved
  (derived from geometry, then geometry itself is dropped). A view is not "done"
  until a **layout-fidelity test** asserts every in-scope item, frame and heading
  renders, *in the form's order*, as its mapped component type.
- **[ADR-0013](adr/0013-read-only-is-a-state-not-a-type.md): read-only is a state,
  not a component type.** A read-only slice still renders the control the
  classifier mapped — a disabled `RadioButtonGroup`, not a `TextField` printing
  `CASH`. The component type comes from the ruleset; read-only is just a runtime
  state on top.

The lesson generalizes well past this form: **a behaviour-only test passes even
when the screen is wrong.** Layout fidelity needs its own gate.

---

## The method: three skills, one validation loop

The reason corrections can be *routed* to the right place is that the tooling is
three separate boxes, not one black box.

1. **`forms-xml-analyzer`** — turns `frmf2xml` output into a faithful, **decision-free
   structural spec**: every block, item, trigger, relation, LOV, frame and label,
   recorded verbatim. It has no Vaadin knowledge and makes no migration decisions.
   Its completeness guarantee is at **form-object grain** — every node in the tree
   is accounted for, testable against the XSD. (For ORDERS: 183 elements, 4 blocks,
   34 items, 45 triggers, 1 LOV.)

2. **The Forms→Vaadin mapping ruleset** (`migration-rules.yaml`) — the published
   IP. A first-match-wins decision tree that maps each Forms construct to a Vaadin
   target: `Text Item` + `DataType=NUMBER` → `NumberField`; LOV-bound item →
   `ComboBox`; `Radio Group` → `RadioButtonGroup`; Forms frame → grouping section;
   table audit trigger → stays in DB; and so on. It is property-aware (a number
   text item and a multiline text item map differently) and it carries an explicit
   **"Aura ignore list"** so visual attributes have *one* disposition, not dozens
   of bespoke ones. **This file is the living substrate of the
   generalizable-vs-specific sort** — every generalizable fix lands here.

3. **Generic spec-driven-dev skills** — consume spec + ruleset, cluster form
   objects into **construct-grain tickets** (~7, one per thing an architect
   recognizes: master block, detail block, filter bar, LOV, totals, locking),
   publish them as GitHub issues, and generate code. The clustering step *is* the
   architectural-decision step; it keeps the backlog reviewable instead of a wall
   of forty "migrate KEY-NEXT-ITEM" non-tickets.

### The build loop

Per construct — riskiest first — not per whole-POC:

1. **Generate** the construct from its ticket.
2. **Validate** against the live Forms app (the behavioural oracle) and **diagnose
   which layer failed**: bad ticket → fix the *ruleset*; ticket right but code
   buggy → fix the *execution skill*; irreducible form quirk → *hand-fix, touch no
   skill*.
3. **Sort every fix** — generalizable into the ruleset (regenerate; now works for
   every form) or specific (stays manual, logged).
4. **Stop** when it compiles and matches the live form on the spine flow. Cap
   refinement at ~2 passes per construct. If a construct won't converge — **stop
   and write it up**. Non-convergence is the most valuable finding, not a reason
   to grind.

---

## When a Forms pattern has no obvious web shape: prototype it

Most constructs have a defensible target in the ruleset. Some don't — and the
riskiest mistake is to let the *first* idea harden into code.

The clearest example was the master block itself. Forms' `S_ORD` block is
**single-record** Execute/Enter-Query: you query, you land on one order, and a
record scrollbar walks you through the result set. The web has no native idiom for
that. There are at least three plausible renderings, and they imply very different
apps — picking wrong shapes navigation app-wide.

So before writing a line of Vaadin, we built a **throwaway prototype**: one static
HTML page, three radically different UI variants toggleable from a single switcher,
all running off the same mock order data. Cheap to build, cheap to throw away,
and — crucially — something you can put side by side against the live Forms app.

| Variant | Shape | Verdict |
|---|---|---|
| **A — lookup search → one order + prev/next** | Quick search + advanced filter panel; one match loads a header `FormLayout` + line-items grid; prev/next walks the result set. | **Chosen.** It's what the `block-master-single` ruleset rule already encodes, and it keeps single-record fidelity — QBE becomes a curated filter, the record scrollbar becomes a prev/next seam. |
| **B — results grid → inline detail** | Search returns a grid of orders; clicking a row loads its detail underneath. | Rejected. More "webby", but it resurrects a master grid the ruleset explicitly rejects and breaks single-record fidelity. |
| **C — search list → routed detail page** | A results list you navigate *from*; the order opens on its own focused route. | Rejected. The most conventional web CRUD shape — a good fit for a *different* app, but too far from the Forms original for a migration claiming fidelity. |

The prototype's job is to make the decision *visible and reversible* — you compare
three real screens instead of arguing about three descriptions. Once Variant A won,
the verdict was recorded (with explicit reasons for the rejections), the winning
shape was folded into the real `OrderView`, and the two losing variants were
deleted. The prototype survives as a read-only mock of the chosen shape — **the
prototype code was never promoted as-is.**

Two rules keep this phase honest:

- **Prototype the pattern, not the product.** It's a sketch to settle one question
  (here: "what is the faithful web rendering of single-record Execute/Enter-Query?"),
  built in throwaway HTML, not the real stack. It is allowed to be ugly and fake its
  data.
- **The decision flows back into the ruleset.** Variant A won *because* it matched an
  existing rule, and that rule's guidance was then enriched with the quick+advanced
  search affordance the prototype validated. A prototype that discovers a genuinely
  new pattern earns a new rule (and, if it overturns one, an ADR). **The prototype is
  where an unmapped pattern becomes a mapped one.**

---

## What we actually built, construct by construct

Each of these shipped behind a real browser test (Playwright) running against a
real Oracle (Testcontainers), exercised end-to-end through all five layers:

- **#10 Quick search → read-only header** — the tracer bullet: type an order id,
  load one order through the whole stack into a read-only header.
- **#11 Read-only line-items grid** — `S_ORD_S_ITEM` coordination; header and
  lines load and reload together.
- **#12 App shell + layout topology** — canvas / toolbar / CONTROL block folded
  into a Vaadin app shell.
- **#13 Prev/next pager** — "record N of M" over the result set.
- **#14 Advanced search panel** — a curated filter bar with Clear, replacing
  Enter-Query; navigation reflects the active search.
- **#15 Order total** — the `Sum` summary as a pure domain method (`Order.total()`)
  plus a live grid footer.
- **#16 Inline line-item editing** *(in progress)* — editable quantities, add/
  delete lines, per-order id assignment, live total recompute, and a
  cascade-delete guard (`CascadeDeleteBlockedException`).

That was **v1's** endpoint: inline editing in progress, and the remaining spine —
product swap via the LOV (re-defaulting price and description) and the `ORA_ROWSCN`
commit path — still ahead. Which is exactly where the interesting part begins,
because we then ran the whole migration a second time.

---

## Running it twice: what v2 changed

The sharpest possible test of "generalizable fixes compound" is to run the migration
twice and watch the second pass inherit the first pass's lessons. So we did. **v1**
was the first full attempt at the ORDERS slice. We sorted every correction, pushed
the generalizable ones back into the ruleset and skills — then **regenerated the
whole slice from scratch as v2**, keeping v1 as a side-by-side baseline. v2 is not a
patched v1; it is what the *smarter tool* produces on the same form.

Three of v1's corrections were generalizable, and you can trace each one from a v1
hand-fix, into the tool, to a v2 payoff.

### 1. Layout fidelity: discovered late in v1, designed-in for v2

The *counted ≠ rendered* gap described above was found **mid-build** in v1 — the
header had already drifted (fields reordered, the "Order Information" group and its
title gone, `PAYMENT_TYPE` flattened to a text field) before anything caught it. v1's
fix was a retrofit: restore the header by hand, then bolt a layout-fidelity gate onto
the method after the fact ([ADR-0012](adr/0012-structural-layout-fidelity.md) /
[ADR-0013](adr/0013-read-only-is-a-state-not-a-type.md)), and record the correction
in a regenerated *v2 PRD* kept alongside the original as evidence.

In v2 that gate is **front-loaded**: structural fidelity ships *inside* the first
structural slice — the app-shell + layout-topology ticket (#41) — instead of being
patched in later. The lesson stopped being a scar and became a starting condition.

### 2. Record actions: a v1 hand-fix that became a ruleset rule

This is the cleanest single demonstration of the whole thesis.

In v1, "create a new order" was a **one-off, hand-written** flow on the presenter
(`newOrder()` builds a blank header). There was **no Duplicate at all**, and although
a delete path existed in the service it was never wired to a visible control — a
deletable block shipped with no Delete affordance. A classic *un-sorted* hand-fix: it
made *this* form work and taught the tool nothing.

So we sorted it — and the rule behind it *can* be stated in form-construct terms:
**"any base-table block whose CRUD capability permits insert/delete gets New /
Duplicate / Delete record actions, whether or not a `KEY-CREREC` / `KEY-DUPREC` /
`KEY-DELREC` trigger exists."** That earned it generalizable status. It landed as a
new `blockActionClassifiers` section in `migration-rules.yaml` (the `METHOD: derive
record actions (Delete/Duplicate/New) from block CRUD` commit). The `KEY-*` triggers
were demoted from being *the* create/delete path to mere **refinements** — a confirm
dialog, a field-reset clear-list, a keystroke binding.

v2 regenerated against that rule and got the full **New / Duplicate / Delete** family
for free — master *and* line level, the Delete action correctly composed with the
Non-Isolated `S_ORD_S_ITEM` cascade-delete guard, and the Duplicate clear-list
encoded as pure domain factories (`Order.duplicateForInsert()`). One hand-fix in v1
became a capability every future form inherits.

### 3. Human-judgment work: from reactive to a first-class output

v1 handled the work only a human can sign off on **reactively** — drift was caught
during the build; deferred dependencies stayed implicit. v2 makes it an explicit,
up-front deliverable. A new `migration-review` skill
([ADR-0014](adr/0014-hitl-review-tickets.md)) emits **`ready-for-human` review
tickets** alongside the `ready-for-agent` build tickets, in three kinds:

- **Careful-review / manual-test** — the behavioural-parity and layout-fidelity
  checks a human must certify side-by-side against live Forms. The AI can compile and
  unit-test a construct; it cannot certify the screen *behaves* and *reads* like Forms.
- **Out-of-slice / deferred follow-up** — the form's accepted-but-deferred
  dependencies (the `m_summit` menu, the `pick` called form, the `CALENDAR` library)
  become tracked forward work, not a silent drop — and deliberately kept in a
  *different lane* from `.out-of-scope/` *rejections*. A human makes the
  defer-vs-reject call.
- **Generalization-review** — generic Forms affordances the ruleset put into a
  *specific* view (the record actions above, the Execute-Query filter bar, the
  `ORA_ROWSCN` commit path, the `CLIENT_IDENTIFIER` audit bridge) flagged for a
  reusable layer.

That third kind is the key move: it applies the generalizable-vs-specific sort
**proactively, at design time** — flagging a generalizable fix *before* it is ever
hand-coded into a view, which is precisely the entry point v1's reactive, fix-time
sort lacked. The "nothing dropped silently" guarantee now extends to the *slice
boundary*, not just the form-object census.

### And v2 finished the spine

v1 stopped with inline editing in progress. v2 closed the rest: the product LOV +
`WHEN-VALIDATE-ITEM` defaulter (#46), master-header editing with the credit-check and
ship-date guards (#47), the whole-graph `COMMIT_FORM` save under the `ORA_ROWSCN`
check (#48), and the record actions (#49). The architecture tightened along the way —
the presenter no longer holds header working-state (`commit`, `lookupCustomer`,
`paymentAllowed` now take explicit arguments and *return* values instead of mutating a
field), and a successful commit round-trips the whole reloaded graph, so you can keep
editing and re-commit without a second load.

### The point of running it twice

v1 → v2 is the **ratio made visible**. Every improvement in v2 traces to a v1
correction that was *sorted correctly* — and the ones that generalized (layout
fidelity, record actions, the review-ticket discipline) now apply to **every** form,
not just ORDERS. A Forms shop doesn't get to re-run its own history — but it inherits
the accumulated ruleset, so its *first* pass starts where our *second* one did.

---

## Reproducibility: a guaranteed tier and an honest tier

- **Tier 1 — guaranteed.** Clone, `docker compose up`, run. The Summit slice runs
  against a self-contained **Oracle Free** container seeded from a committed dump
  (75 tables, ~54k rows; the POC slice — `S_ORD` 3,725 rows, `S_ITEM` 12,509 —
  loads intact). A stranger needs Docker, not an Oracle licence or your Forms DB.
- **Tier 2 — honest.** The skills + the mapping ruleset + the actual prompt
  transcript that produced this. Applied to *your* form, the method gives you a
  **classified migration backlog** — recommended target per construct, decisions
  flagged, nothing dropped — **not a finished app.**

The single most trust-building sentence we can write: *this reproduces the Summit
migration exactly; on your own form it gives you a documented head start, not a
finished product — and here's precisely where you'll still work.*

---

## What this means for a Forms shop

If you are sitting on a few hundred Oracle Forms, the takeaway is not "AI will
rewrite them for you." It is sharper and more useful than that:

- AI does a large fraction of each construct **unaided**, and that fraction is
  measurable.
- The corrections it needs sort cleanly into *teach the tool once* vs *fix this
  one form by hand* — and the first bucket compounds across your whole form
  estate.
- The genuinely hard parts (arbitrary QBE, modal validation semantics,
  pixel-fidelity) are **knowable in advance** and small in number. You can plan
  around them instead of discovering them mid-project.

The ratio is the number. This POC is how you measure it on one form before you
bet a migration program on it.

---

*Built with Vaadin 25, Spring Boot 4, Oracle Free, and a set of composable AI
migration skills. The ruleset, the ADRs behind every decision above, and the full
spec for the ORDERS form are in this repository.*
