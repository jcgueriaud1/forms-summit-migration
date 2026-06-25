# Oracle Forms → Vaadin: AI-Assisted Migration POC

> **Headline:** Can an Oracle Forms master-detail block — an order with its line
> items, with trigger-driven totals and an LOV-backed product lookup — become a
> Vaadin Grid-and-form, and where does it break?

A two-week, time-boxed proof of concept migrating one representative slice of the
Oracle **Summit** demo (the `ORDERS` form: `S_ORD` master / `S_ITEM` detail) to
Vaadin 25 + Spring Boot, with the migration driven by composable AI skills.
Destined for publication on vaadin.com as a credibility piece for Forms-shop
architects evaluating modernization.

---

## The finding (what this POC actually argues)

The migration is the vehicle. The finding is the payoff:

**AI-assisted modernization is a sorting problem.** Every correction you make to
generated code is one of two things:

- **Generalizable** — a rule the tool *should* have known. Push it back into the
  mapping ruleset; it now works for every form.
- **Specific** — an irreducible quirk of *this* form. Hand-fix it, log it, move on.

The skill is the accumulated generalizable set, and it gets smarter every time a
fix is sorted correctly. The **ratio** of generalizable-to-specific fixes is the
single number the audience cares about, because it is their automation ceiling.

The write-up leads with the concrete construct (the headline) and lands on this
general principle. A fix earns "generalizable" status only when the rule behind
it can be stated in form-construct terms (e.g. "any table-level audit trigger
stays in the DB"). If the rule can't be articulated, it is **specific by
default** — this conservatism is what separates a reusable skill from one
secretly overfit to Summit.

---

## Resolved architecture

### The three headline constructs

| Forms construct | Vaadin target | Notes |
|---|---|---|
| **Execute Query / Enter Query** (F7/F8) | Persistent **filter bar** above the master Grid + **lazy paged DataProvider** | Replace, don't replicate the modal mode. Credibility hinge: must demonstrably page against the DB, not load the whole table. Honest loss: arbitrary QBE expressiveness → curated filter set. |
| **Product LOV** | **LookupField** (Component Factory add-on, v6.x for Vaadin 25); default interaction = type-ahead, grid available for browse | Near-direct analogue of the LOV idiom. **Day 2–3 go/no-go: confirm lazy paging works through the add-on's combobox *and* grid.** Fallback: core ComboBox + separate Grid. Disclose: it's a community add-on, not core. |
| **Master-detail block** | Master = single-select Grid with `setDeselectAllowed(false)`; detail = **Grid Pro** inline editing | "Always a current record" via deselect-disabled. Grid Pro commits per-cell; Forms validates per-item on navigation — documentable seam. Grid Pro is commercial — legitimate to showcase on a Vaadin piece. |

### Transaction & locking model

- Commit the master + detail graph as **one transaction**, mirroring a Forms `COMMIT`.
- **Optimistic locking via `ORA_ROWSCN`** — read on load, re-check in
  `UPDATE … WHERE id = ? AND ORA_ROWSCN = ?`; zero rows updated = conflict →
  rollback. **No schema change** (the credibility claim: "we did not touch your
  database"). Document the `ROWDEPENDENCIES` caveat (block-level vs row-level SCN
  precision) as an honest finding.
- **Spine flow (must run):** load an order → edit line quantities → swap a product
  via LookupField (re-default price + description) → add/delete lines → totals
  recompute live → commit under the `ORA_ROWSCN` check.
- **Stretch (documented, fine if half-built):** create a new order from scratch
  (customer LOV + `S_ORD` sequence ID + insert ordering).

### Connection & identity

- **HikariCP pool under one service account** (not per-user sessions).
- Set **`CLIENT_IDENTIFIER`** per request from the Spring Security principal
  (~5 lines), so `SYS_CONTEXT('USERENV','CLIENT_IDENTIFIER')` still returns the
  real human despite pooling — the bridge that keeps DB-side identity alive.
- This is what lets the **kept audit trigger keep recording the right actor**,
  *provided it reads `CLIENT_IDENTIFIER` rather than `USER`*. (Confirm which at
  build time — see parked task.)
- Oracle proxy authentication documented as the heavier full-fidelity
  alternative; not built.

### Server-side DB objects (boundary rule)

Rule: **table-level / data-integrity logic stays in the DB; form-serving logic
moves to the Java service layer; logic the new architecture absorbs simply
dissolves.**

- **Audit trigger (AFTER INSERT)** → **stays in DB.** Fires transparently inside
  the Vaadin commit's transaction; zero Java written. ("Some things you don't
  migrate, and they keep working.")
- **`get_product_image` function** → **dissolves** into the product lookup query
  (the DataProvider already fetches the row; the filename comes along).
- **Procedure** → TBD, parked (see below). Default: form-serving → service layer.

### Application layering

```
View  ←  Presenter  ←  Service  ←  Domain  ←  (JdbcClient)  ←  Data
```

- **Domain** owns the *formula*: `orderItem.getExtendedPrice()` (qty × price),
  `order.recalculateTotal()`. Pure, unit-testable. This is where
  `WHEN-VALIDATE-ITEM` PL/SQL lands.
- **Presenter** owns *orchestration*: listens for Grid Pro cell-commit, writes
  the edited value into the in-memory bean, calls `recalculateTotal()`, pushes
  the new total to the display. (Calls the domain method — does **not** contain
  the formula.)
- **Service** owns *load and commit*: fetch the graph; flush transactionally with
  the `ORA_ROWSCN` check.
- **Data** = Spring **`JdbcClient`** (chosen over jOOQ to avoid Oracle-dialect
  licensing).

**Collection shapes:** master result = **lazy paged** (unbounded `S_ORD`); detail
= plain **in-memory `List<OrderItem>`** (bounded; one order's lines). Editing the
in-memory list and flushing only at commit is the faithful analogue of the Forms
client-side block buffer — "save later" is correct, not a shortcut.

---

## Skill architecture

Three separate boxes — which is *why* the validation loop can route a fix to the
right place.

1. **`forms-xml-analyzer`** *(build this — the engineering lift)*
   `frmf2xml` XML → faithful **structural spec** (every block, item, trigger,
   relation, LOV). Mechanical, testable against the XSD, no Vaadin knowledge.
   Inventory is at **trigger grain** (completeness guarantee — nothing slips).

2. **Forms→Vaadin mapping ruleset** *(the published IP — this grill session, frozen)*
   The decision tree above, as one inspectable artifact: table trigger → stays;
   form-serving logic → service; Execute Query → filter bar; pessimistic →
   `ORA_ROWSCN`; LOV → LookupField; etc. Maps **1:1 to the scorecard** and is the
   living substrate of the generalizable-vs-specific sort. Independently
   publishable — the thing a reader screenshots and applies to their own form.

3. **Generic spec-driven-dev skills** *(off-the-shelf; refine only if they fall short)*
   Consume spec + ruleset → cluster into **construct-grain tickets** → GitHub
   Issues → code.

**Ticket grain:** inventory at trigger grain (in the spec), **tickets at construct
grain** (~7, 1:1 with scorecard rows and skills). The clustering step *is* the
architectural decision step. Avoids a wall of forty "migrate KEY-NEXT-ITEM"
non-tickets and keeps each ticket independently reviewable/testable.

**Transport:** GitHub Issues in the Vaadin-org repo, generated programmatically;
the **markdown body is authored by the planner** so content is portable (a
GitLab/Jira shop reuses the same spec + bodies, different `create` call).

---

## The build loop (regenerate → validate → sort)

Per **construct**, not whole-POC. Riskiest construct first.

1. **Generate** the construct from its ticket.
2. **Validate** against the live Forms app (the behavioral oracle), and
   **diagnose which layer failed**:
   - ticket wrong (bad decomposition / wrong call) → fix the **mapping ruleset**;
   - execution wrong (ticket right, code buggy) → fix the **execution skill/prompt**;
   - form quirk no skill should generalize → **hand-fix, touch no skill**.
3. **Sort every fix**: generalizable → push into the skill and regenerate
   (works for every form); one-off → stay manual, log in the scorecard.
4. **Stopping gate:** compiles **+ behavioral parity on the spine flow**, checked
   side-by-side against the live Forms app. Cap planner-refinement at **~2 passes
   per construct**. If a construct won't converge — **stop and write it up**.
   Non-convergence is the most valuable Step 5 finding, not a reason to grind.

Reserve a **full** rebuild only for cross-cutting errors (layering wrong, locking
wrong) — found by migrating the riskiest construct on day 3 while a rebuild is
still cheap.

---

## Scorecard (Step 5 spine — capture live, sorted, from day one)

One row per construct (master block, detail block, filter-bar, LOV/LookupField,
trigger totals, locking model, kept/dissolved DB objects).

| Column | What it records |
|---|---|
| **Construct** | The Forms thing, named in Forms terms (the reader must recognize their own app). |
| **Vaadin target** | What it became. |
| **AI did unaided** | Correct, usable-as-is output. The honest *win*. |
| **Needed hand-fixing** | Wrong/incomplete output + the correction. Split: **generalized-into-skill** vs **stayed-specific**. The ratio = the automation ceiling. |
| **Genuinely hard / can't automate** | Where the construct resists tooling regardless of AI quality. The column a skeptic respects most. |
| **Behavioral parity** | Did the migrated construct match the live Forms app side-by-side? Proof it *runs*, not just compiles. |
| **Verdict** | `Clean` / `Assisted` / `Manual` / `Stays-in-DB`. |

Rejected columns: "% automated" (false precision) and per-construct time-spent
(unrecordable accurately under crunch; a wrong number is worse than none).

---

## Reproducibility (two-tier README — disclaim one-click)

- **Tier 1 — guaranteed:** clone → `docker compose up` → the Summit slice runs
  against a self-contained **Oracle Free 23ai** container, seeded by a committed
  `init.sql` (the four tables + sequences + enough rows to load one order with
  several lines and exercise a product swap + commit). A stranger needs Docker,
  not an Oracle license or your Forms DB.
- **Tier 2 — honest:** the two skills + the mapping ruleset + **the actual prompt
  transcript** that produced this POC. A developer applies the *method* to their
  form and gets a **classified migration backlog** (recommended target per
  construct, decisions flagged, nothing dropped) — *not* a finished app. The
  scorecard is the expectation-setter for what still needs hands.

The single most trust-building sentence in the repo: *"this reproduces the Summit
migration exactly; on your own form it gives you a documented head start, not a
finished product — and here's precisely where you'll still work."*

Build against the **live Oracle + Forms DB** (fast path + behavioral oracle); the
live DB is **never** what ships.

---

## Two-week sequence (front-load the invalidating risks)

| Days | Work | Risk surfaced |
|---|---|---|
| **1–2** | `forms-xml-analyzer` → spec → planner → **GitHub Issues**. Backlog exists before code. | Does the analyzer survive real Summit XML? (Earliest, cheapest credibility artifact.) |
| **3–5** | Riskiest construct first: **master-detail block + totals**, and the **LookupField lazy-paging go/no-go**. | Is the architecture/skeleton right — while a full rebuild is still cheap? |
| **6–8** | Remaining constructs: filter bar, LookupField proper, `ORA_ROWSCN` commit path, `CLIENT_IDENTIFIER` bridge. | — |
| **9–10** | Tier-1 reproducibility (`init.sql` + `docker compose`), side-by-side parity validation, parked DB-objects task (~2h). | — |
| **11–12** | Write-up (mostly *assembly* if the log was kept honestly) + repo/README polish. | — |
| **Throughout** | **Migration log — captured live and sorted** generalizable-vs-specific. | This is the point-of-failure discipline (see below). |

Confirm before starting: any hard demo date, unavailable days, or internal review
checkpoint that should reshape this.

---

## Parked tasks (non-blocking)

- **Classify the three server-side objects** (~2h, post-migration): audit trigger
  stays; `get_product_image` dissolves; procedure TBD. Confirm the audit trigger
  captures the actor via `USER` vs `SYS_CONTEXT` (decides whether
  `CLIENT_IDENTIFIER` is a real finding or free).
- **`S_ORD` total: stored column or derived?** One-line build-time confirmation.
  Computed from in-memory lines either way; only delta is whether commit also
  writes the column.
- **Product image display:** is it actually shown in the ORDERS slice (real image-item
  migration) or is the filename incidental (nothing)?

---

## The one discipline that can sink this

The **migration log is the point of failure.** Every architectural choice assumes
you capture — live and sorted — what the AI did, what you fixed, and which bucket
each fix went in. Skip it for three days under crunch and Step 5 (the most
important section, and the actual thesis) gets reconstructed from memory, which is
exactly the dishonesty the piece exists to avoid. The log is **instrumentation you
run from day one**, not documentation written at the end. If anything slips,
protect the log before the polish.
