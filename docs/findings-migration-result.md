# Findings — what went right and wrong in the migrated ORDERS app

A retrospective on the **result** of the Oracle Forms → Vaadin migration, not the method.
Scope is deliberately narrow: *the migrated application itself* — what actually ships in
the `v2` branch, what was consciously left out, and what fell out silently. The method
story (the ratio compounding, the skills, the ledger machinery) lives in
`docs/blog-post-migration.md`; this document is the honest audit of the artifact.

> **What "the migrated app" is.** The shipped app is the **`v2` branch** — the
> `S_ORD` / `S_ITEM` master-detail spine of `ORDERS.fmb`. **`v3` was designed but never
> built**: its PRD (`docs/prd-orders-view-v3.md`), its disposition ledger
> (`docs/generated/ORDERS.ledger.md`, 151 rows), and ADR-0015 exist on `master`, but the
> v3 build tickets (#85–#95) never landed — the highest ORDERS build issue in git history
> is #59. So everywhere below, **v3 = the plan that catches v2's gaps**, and the ledger's
> `migrated-as` rows citing #85–#95 describe v3's *intent*, not v2's reality.

---

## The one-line finding

> **v1→v2 proved fixes compound. The v2→v3 gap proved something scarier: the loss you
> never argue about in review is the one nobody noticed was gone. The question is not
> "how do we fix the misses we catch" — it's "how do we not miss essential features
> without knowing it."**

Everything below is organised around that. The migrated parts are genuinely good. The
missing parts split cleanly into two kinds — **deferred-and-tracked** (fine) and
**silently dropped** (the real finding) — and the difference between those two kinds is
the entire lesson.

---

## 1. What went right — the migrated parts

### 1.1 PL/SQL business logic → pure, tested Java (faithful)

Every Forms trigger/formula on the spine has a traceable Java counterpart, and the
business rules were pushed into **pure Domain types** (ADR-0001) that unit-test without
Spring, Vaadin, or a database.

| Forms construct | Java home | Verdict |
|---|---|---|
| `ITEM_TOTAL` formula (`quantity_shipped * price`) | `OrderLine.itemTotal()` | ✅ uses *shipped* qty, matching the spec's emphasized note |
| `TOTAL` summary (`Sum`) | `Order.total(List)` | ✅ pure stream sum; static overload lets the live editor recompute |
| `WHEN-VALIDATE-ITEM` (product defaulter) | `OrderPresenter.applyProductDefaults()` + `ProductService` | ✅ `NO_DATA_FOUND → RAISE` becomes a rejected field |
| `WHEN-VALIDATE-ITEM` (customer lookup) | `OrderPresenter.lookupCustomer()` + `CustomerService` | ✅ |
| `WHEN-VALIDATE-RECORD` (ship-date guard) | `Order.shipDateValid()` | ✅ mirrored in a Binder validator **and** re-checked in `OrderService.save()` |
| Credit-rating rule | `PaymentEligibility.allows()` (pure) + presenter fetch | ✅ clean data/decision split |
| `PRE-INSERT` (`MAX(item_id)+1` per order) | `Order.nextItemId(...)` | ✅ computed over working lines, not a re-query |
| Delete cascade guard (Non-Isolated) | `Order.hasLineItems()` + `CascadeDeleteBlockedException` | ✅ |
| Optimistic locking (`ORA_ROWSCN`) | `Order.scn` + `updateHeaderIfUnchanged` → `SaveResult.Conflict` | ✅ pessimistic-across-think-time → optimistic, no schema change |
| `COMMIT_FORM` | `OrderService.save()` — one `@Transactional` whole-graph save | ✅ |
| `POST-QUERY` display re-fetches | dissolved into JOINs in `loadGraph` | ✅ no N+1 |
| `AUDIT_S_ITEM` trigger | **left in the DB**, fires in-transaction, zero Java | ✅ clean boundary |

The domain formulas quote almost 1:1 from the PL/SQL. `itemTotal()` is
`price.multiply(BigDecimal.valueOf(quantityShipped))`; the credit rule is a
set-membership check against `{GOOD, EXCELLENT}`. **No `TODO`/`FIXME`/`HACK` markers
exist anywhere in `src/main/java`.**

### 1.2 Architecture and concurrency (the real engineering)

The substance of the migration is *not* the formulas (see §3.1) — it's the plumbing, and
that plumbing is solid:

- **Whole-graph commit** (`OrderService.save`): validate-gate first, scn-guarded header
  `UPDATE` as the first write so a conflict leaves the DB untouched, then `reconcileLines()`
  diffs persisted vs. working item-ids into inserts/updates/deletes.
- **Optimistic-locking translation** with a typed `SaveResult` (Committed / Conflict /
  Invalid) rather than a silent overwrite.
- **In-memory editing session** with re-entrancy guards (`syncingHeader` /
  `syncingProduct` / `syncingSelection`) in the view.
- Clean **View ← Presenter ← Service ← Domain ← Data** layering throughout.

### 1.3 Layout fidelity (ADR-0012 / ADR-0013)

The header is structurally the same screen, not a re-imagining:

- "Order Information" `H2`, the `RECT59` frame as a titled section, `RECT60` as the items
  section, fields in **`layoutPlan` (Y-then-X) order**, not declaration order.
- **Control kinds preserved**: payment type → `RadioButtonGroup` (not flattened to text),
  order-filled → `Checkbox`, dates → `DatePicker`, product → `ComboBox` LOV with typed-id
  fallback. Read-only fields kept as their control kind in a read-only *state*.
- Every construct shipped behind a **Playwright + real-Oracle (Testcontainers)** test
  exercising all five layers, plus a **layout-fidelity gate** (every in-scope item/frame/
  heading renders, in order, as its mapped control).

**On the user's hypothesis "the layout migration looks ok" — confirmed**, with one
maintainability caveat in §3.2.

### 1.4 AI-assisted comprehension of Forms semantics (an under-credited win)

A quieter win that doesn't show up in a diff: the AI was genuinely useful for
*understanding what a Forms construct means* before deciding how to migrate it — the
semantics of an idiom, not just its syntax. Forms behaviours that a web developer
has no mental model for were surfaced and explained, which is what let the migration
make **conscious** decisions instead of accidental ones.

The clearest example is **validation-driven navigation**. In Oracle Forms,
`WHEN-VALIDATE-ITEM` / `WHEN-VALIDATE-RECORD` are **navigation-blocking**: the
trigger fires as focus leaves the item/record, and on failure it raises
`FORM_TRIGGER_FAILURE`, which *keeps the cursor where it is* — the user physically
cannot move off an invalid field. Validation and navigation are the same event.

The migrated app deliberately **does not** replicate that coupling:

- Field validation runs through a **Binder** (field-at-a-time, non-blocking — an
  invalid field is flagged, but focus is free to move).
- Line edits **commit per cell** in the grid, rather than gating on record-exit.
- The real integrity guarantee moves to a **save-gate** in `OrderService.save()`
  (`shipDateValid()` re-checked before the whole-graph commit), so nothing invalid
  is ever persisted — the *when* of enforcement changed, the *what* did not.

This is the same "documentable seam" the blog post names (Grid commits per cell;
Forms validates per item on navigation). The finding worth recording *here* is that
**recognising it as a seam at all depended on first understanding the Forms
semantic** — that blocking navigation is a validation mechanism, not incidental UX.
Left unexplained, it would have been an easy silent behaviour-change; understood, it
became a deliberate, defensible parity decision.

---

## 2. What's missing — and the distinction that matters

Of the **151 ledger rows**: **74 migrated-as, 57 deferred, 16 obsolete, 4 re-decided**.
So the migrated app is roughly **half the form** by object count — deliberately, the
`S_ORD`/`S_ITEM` spine. Missing things fall into two very different buckets.

### 2.1 Deferred and tracked — this is fine

Consciously scoped out, each with a tracking row, none pretending to be done:

- **`S_INVENTORY` sub-detail** (auto-query stock) — its own follow-up PRD.
- **Product image** — filesystem TIFF, needs TIFF→PNG/JPEG conversion; default-hidden.
- **`CV_HELP` help canvas, `EXIT_BUTTON`, the `m_summit` menu module, the `pick` called
  form.**
- **Date LOVs** collapsed to `DatePicker` (no query to migrate).
- **`IMMEDIATE` / `AUTO_QUERY`** runtime coordination checkboxes.
- **`CLIENT_IDENTIFIER` / proxy-auth bridge** — dropped because the audit trigger reads
  no actor.
- **Arbitrary QBE Enter-Query** → a curated advanced-search panel (recorded honest loss).

This bucket is healthy. Deferred ≠ dropped: a human made the defer-vs-reject call and it's
tracked forward.

### 2.2 Silently dropped — the real finding

The v2 retrofit audit found **four losses that every guarantee sailed past**. These are
absent from the shipped v2 app and were *not* known to be absent:

1. **`GLOBAL.customer_id` calling-context contract** — ORDERS can be launched already
   filtered to a customer (`PRE-QUERY` reads it, `WHEN-CREATE-RECORD` defaults a new
   order's customer, `PRE-FORM` clears it). An entire **cross-form interface** — invisible
   in v2.
2. **`DATE_ORDERED` `$$date$$` default** — a new order should default its ordered-date to
   today. Dropped.
3. **`QUANTITY_SHIPPED` insert-only mode** (`updateAllowed=false, insertAllowed=true`) —
   editable while a line is new, read-only once saved. **Flattened to plain editable.**
4. **`SALES_REP_ID` read-only deviation** — v2 made it read-only, which was the *right*
   call, but **nowhere recorded that this deviated from the spec's "editable."** An
   undocumented right answer is still a hole: the next reviewer can't tell it from an
   accident.

Plus a fifth, worse, finding: **a confident false-absence claim.** v2's code carried a
comment asserting *"S_ORD has no `WHEN-CREATE-RECORD`"* that nothing checked against the
spec. It *does* have one. An invented absence made a silent drop look intentional.

**Why these are different from every v1/v2 correction:** each one **had spec data *and* a
matching ruleset entry.** The tool wasn't missing a rule. The failure was *downstream of
knowing* — **nothing consumed the fact.** And every gate was green:

| Guarantee | What it proved | Why it missed these |
|---|---|---|
| Spec census (183 elements) | every node counted | counted ≠ consumed |
| Scorecard / tickets (construct grain) | "migrate the master block" | too coarse to see one property inside a construct |
| ADR-0014 deferred lane | whole *surfaces* tracked | catches surfaces, not properties |
| Layout-fidelity gate | every field *renders* | a default / insert-only mode has **no visible absence** |

That last row is the crux: **you cannot *see* the absence of a default or an insert-only
mode. The screen looks completely correct without them.**

---

## 3. Stress-testing the good parts (grill-me: challenge the wins)

The migrated parts are real, but the demo flatters them. Here are the challenges worth
running before claiming this generalises — with suggested probes.

### 3.1 "The PL/SQL was migrated well" — true, but the demo is trivial (biggest caveat)

**Confirmed by the source analysis.** The whole form is ~500 lines of PL/SQL, of which
**~half is Oracle-generated master-detail boilerplate** (`ON-POPULATE-DETAILS`,
`ON-CHECK-DELETE-MASTER`, `CLEAR_ALL_MASTER_DETAILS`). Of the ~250 hand-written lines:

- **~34 of 45 triggers are trivial** — a single built-in call, assignment, or SELECT-INTO.
- **~8 are moderate** — a SELECT-INTO + one branch + a `NO_DATA_FOUND` handler.
- **Exactly ONE** hand-written trigger (`PRODUCT_LOV_BUTTON.WHEN-BUTTON-PRESSED`, ~30
  lines) rises to "moderately complex" — and it's *deferred* (the `pick` called form).
- **Zero** PL/SQL packages, dynamic SQL, cursors over large sets, batch/`FORALL`,
  package state, savepoints, or differentiated error handling. Error handling is
  uniformly `WHEN NO_DATA_FOUND THEN MESSAGE(...); RAISE FORM_TRIGGER_FAILURE`.

**What a real enterprise form has that this lacks:** packaged domain logic (pricing, tax,
credit limits, approval workflows in DB packages), dynamic query building
(`Set_Block_Property` DEFAULT_WHERE/ONETIME_WHERE), multi-error-accumulating validation,
`SQLERRM`/`SQLCODE` handling and logging, `ON-INSERT`/`ON-UPDATE`/`ON-DELETE` transaction
overrides, role/security checks, audit-column population, `RUN_PRODUCT` reports.

**Suggested probes to actually challenge this:**
- Migrate a form whose logic lives in a **DB package with state** (a spec+body the AI must
  read, not just an inline trigger) — does the "pure Domain method" translation still hold,
  or does the AI inline package state into the presenter and lose the boundary?
- Migrate a trigger with **dynamic `DEFAULT_WHERE` query building** — does it become a
  clean `OrderSearchCriteria`-style spec object, or a string-concatenation hazard?
- Migrate a form with **`ON-INSERT`/`ON-UPDATE` overrides** (the DB save path is not the
  default) — the whole-graph `save()` assumption breaks here.
- Migrate a trigger that **accumulates multiple validation errors** before failing —
  Binder's field-at-a-time model may not map.
- Feed the AI a **500+ line `WHEN-BUTTON-PRESSED`** with nested loops, cursors, and
  `FORM_TRIGGER_FAILURE` control flow — does fidelity survive, or does it silently
  simplify branches?

**Hypothesis to test:** the *formulas* migrate trivially at any scale; what breaks first in
a real app is (a) **packaged/stateful logic** the boundary can't hold, and (b) the
**volume of decision-prone properties** overwhelming the ledger (see §4).

### 3.2 "The layout looks ok" — true, but `OrderView.java` is ~1158 lines

The layout is faithful, but it's a **single ~1158-line view class** mixing structure and
all event wiring. On a demo that's fine; on a real form with 5× the fields and secondary
canvases it's a maintainability cliff.

**Suggested probes:**
- Does layout fidelity survive **multiple canvases / stacked windows / tab pages**? The
  demo has one real canvas (`CV_ORDER`); `CV_INVENTORY` and `CV_HELP` were deferred, so the
  hard multi-canvas topology case was **never exercised**.
- Does the `layoutPlan` (Y-then-X) ordering hold when frames **overlap or nest**, or when
  fields use **anchored/relative positioning** instead of a clean grid?
- Does a form with **30+ header fields** produce a usable FormLayout, or a wall?
- Is the fidelity gate asserting **grouping and order**, or just *presence*? (Presence-only
  would pass a scrambled-but-complete screen.)

---

## 4. The headline: not missing features without knowing it (grill-me: challenge the fix)

v3's answer to the silent-loss class is the **disposition ledger** (ADR-0015): generated
from the spec, **one row per form object *plus one row per decision-prone mapped
property*** (every default, editability mode, visibility, value-propagation, cross-form
contract). For ORDERS that's 151 rows. Every row must reach a disposition — `migrated-as`
(with a ref), `re-decided` (rationale on the row), `deferred` (tracking ticket), or
`obsolete` (dissolving rule) — **before any build ticket is cut.** `to-issues` refuses to
cut tickets while a row is unassigned; `--check-refs` keeps citations live; each
`migrated-as` row becomes a **runtime acceptance criterion** (a UI test).

This is the right shape: it gates **before code exists**, at the **property grain** that
every other guarantee was too coarse to see. But it is **unproven at scale** — it was
authored *after* v3 was designed and the v3 build never ran. Grill it:

- **Does the analyzer actually *emit* every decision-prone property automatically, or were
  the 151 rows hand-curated?** If a human wrote them, the ledger only catches what that
  human already noticed — which is exactly the failure mode it claims to fix. **This is the
  load-bearing question.** (The ledger `.md` is committed; the generator that produces it
  from `ORDERS.spec.json` should be the thing under test.)
- **What's the row count for a real form?** 151 rows for a 34-item demo → plausibly
  **thousands** for a real module. Who assigns dispositions to 3,000 rows without
  rubber-stamping? What stops "assign all to migrated-as" from becoming the new census?
- **The grain boundary is itself a judgment.** ADR-0015 says don't create rows for
  "mechanical carries" (a max-length, a prompt). But *the four losses were arguably
  mechanical-looking too* (a default, an editability flag). **Who decides which properties
  are "decision-prone," and doesn't that decision reintroduce the silent-loss risk one
  level up?**
- **`re-decided` still relies on a human noticing the deviation.** `SALES_REP_ID` was
  caught by a retrofit audit, not the ledger. Would the ledger have *flagged* the
  spec-says-editable / built-read-only mismatch, or just provided a row to record it once
  someone else spotted it?
- **The false-absence claim (finding #5) is the deepest one and the ledger only half
  addresses it.** `--check-refs` verifies a *cited* ticket is live; it doesn't verify a
  *claim of absence* against the spec. What check catches the next invented "this form has
  no X"?
- **v3 was never built.** Every claim that the ledger "would have caught" the four losses
  is a paper claim. **Suggested probe: actually run the v3 build against ORDERS and
  confirm the four rows force the four features into existence** — that's the only real
  proof, and it's the obvious next experiment.

---

## 5. Bottom line

| | Verdict |
|---|---|
| **PL/SQL fidelity** | ✅ Faithful — but the demo is trivial; unproven on packaged/stateful/dynamic logic (§3.1) |
| **Architecture & concurrency** | ✅ The genuine engineering; solid |
| **Layout fidelity** | ✅ Structurally the same screen — but single-canvas only, ~1158-line view (§3.2) |
| **Deferred surfaces** | ✅ Known and tracked — healthy |
| **The four silent losses** | ❌ Absent from v2, unknown to be absent — the core finding |
| **The ledger fix (v3)** | 🟡 Right shape, right grain, gates early — **but never built and unproven at scale** |

The migrated app is a **credible, well-architected slice** whose business logic happens to
be easy because the source is a textbook demo. The durable lesson isn't in what was
migrated — it's in **how the misses were classified**: deferred-and-tracked is safe; the
danger is the essential feature that has spec data, has a rule, renders as a correct-looking
screen, and still never gets consumed — **the loss nobody notices is gone.** The next step
that would actually settle it is to **build v3 and prove the ledger forces those four
features to exist** — and then run the method on a form with real, packaged, stateful
PL/SQL.
