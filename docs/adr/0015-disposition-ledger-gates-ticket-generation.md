# A disposition ledger gates ticket generation at mapped-property grain

The v2 ORDERS retrofit audit found four losses that every existing guarantee
sailed past: the `GLOBAL.customer_id` calling-context contract (PRE-FORM /
PRE-QUERY / WHEN-CREATE-RECORD), the `DATE_ORDERED` `$$date$$` default, the
`QUANTITY_SHIPPED` insert-only editability mode, and the undocumented
`SALES_REP_ID` read-only deviation. Each had spec data **and** a matching
ruleset entry; each leaked *downstream* — the census counts spec elements but
nothing consumed it, the scorecard and build tickets are cut at construct
grain, and ADR-0014's deferred lane catches *surfaces*, not properties. A
fifth finding compounded the class: code justified one gap with a confident
false absence claim ("S_ORD has no WHEN-CREATE-RECORD") that nothing checked.

We decided that every migration maintains a **disposition ledger** generated
from the structural spec — one row per form object plus one row per
**decision-prone mapped property** (defaults, editability modes, visibility,
value propagation, cross-form contracts) — and that **`to-issues` refuses to
cut build tickets while any row is unassigned**. Each row ends in exactly one
disposition: `migrated-as` (ref to ticket/PR), `re-decided` (rationale +
decider, recorded **on the row**), `deferred` (tracking ref), or `obsolete`
(rule id). `migration-review` re-checks the ledger at review time, and build
tickets carry a `Covers:` list of row ids plus a per-field fidelity table.
The analyzer feeds the ledger the previously-invisible surface: `:GLOBAL` /
`:PARAMETER` references extracted from trigger bodies as `global-variable` /
`parameter` dependencies with per-trigger read/write usage.

## Considered options

- **Gate at `migration-review` only.** Rejected: gaps would surface after the
  build — the same failure timing v2 had. The gate must bite before code
  exists, which is ticket-cutting time.
- **Full element×property grain.** Rejected: rows for every mechanical carry
  (max-lengths, prompts, geometry) would number in the thousands and drown the
  decision-prone rows; humans would stop reading the ledger and the gate would
  be rubber-stamped. Mechanical carries are already verified at runtime by the
  layout-fidelity gate (ADR-0012). Element grain alone was also rejected — it
  would not have caught `$$date$$` or the editability mode, the two
  property-grain leaks.
- **Record re-decisions in the PRD or a per-form decisions doc.** Rejected:
  the PRD predates the build (deviations mostly emerge during it, so the
  section is always stale), and a separate doc is a second place to keep in
  sync with the ledger. The ledger row is where the gate already forces a
  human to look.
- **A verifier script for absence claims.** Deferred, not rejected: regex over
  prose is brittle. The enforceable core — any "Forms has/hasn't X" claim must
  cite the spec, and `migration-review` verifies each — plus the ledger itself
  (every trigger has a row, removing the motive to claim absence) covers the
  failure mode observed.

## Consequences

- "Nothing dropped silently" now holds at three grains: spec (census,
  analyzer), slice boundary (deferred-surface tickets, ADR-0014), and
  migration content (ledger rows, this ADR).
- The ledger is regenerable: dispositions carry forward by row id across spec
  regenerations, so enriching the analyzer never wipes decisions.
- Deviations become auditable: a `re-decided` row is distinguishable from an
  accident, and an undocumented deviation found at review time has a defined
  home. Generalizable re-decisions still flow into `migration-rules.yaml` as
  the new behaviour, per the existing convention.
- Ticket-cutting gains a hard precondition and some friction: for a form the
  size of ORDERS the ledger is ~150 rows, of which the human-judgment subset
  (re-decided, defer-vs-reject) is small; the rest are assignable by the agent
  and confirmed in bulk.
- A `migrated-as` disposition proves *decided*, not *delivered*: every
  `migrated-as` row a build ticket covers becomes an acceptance criterion
  asserting the named behaviour at runtime (the UI-test convention), and
  `migration-review` treats a migrated-as row no test asserts as a
  careful-review item. Property rows are the ones that slip otherwise — the
  screen looks right without a default or an insert-only mode.
- Refs are checked for liveness (`--check-refs`): a cited ticket must exist,
  and a `deferred` row must cite at least one still-open ticket — a closed
  deferred ref means the row flips to `migrated-as` or is re-deferred. This
  keeps the ledger from rotting into confident-looking stale pointers.
- The insert-only editability mode gets a first-class ruleset meaning
  (`forms-insert-only-field`): `updateAllowed=false, insertAllowed=true` is a
  record-mode state flip, not `setReadOnly(true)` — collapsing it is a
  recorded fidelity loss.
