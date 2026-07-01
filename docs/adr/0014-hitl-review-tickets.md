# The migration emits HITL review tickets, not just build tickets

The method produces `ready-for-agent` **build tickets** (one per construct, 1:1 with
the scorecard) and, in the build loop, sorts each *fix* generalizable-vs-specific
**reactively**. That leaves the human-owned work implicit. We decided the method
also emits, up front, three kinds of **`ready-for-human` (HITL) review tickets** —
owned by the `migration-review` skill — because each turns on a judgment the AI is
not entitled to make:

1. **Careful-review / manual-test** — behavioural parity against the live Forms app
   and the layout-fidelity gate (ADR-0012/0013). The AI can compile and unit-test a
   construct; it cannot certify the screen *behaves* and *reads* like Forms, nor
   judge a `Refactor`/`Unsupported`/`escalate` outcome. Derived from PLAN.md's
   documented seams (Grid Pro per-cell commit vs Forms per-item validation;
   `ORA_ROWSCN` block-level SCN precision), the spine flow, the fidelity gate, and
   ruleset escalations.
2. **Out-of-slice / deferred follow-up** — the form's dependencies that are accepted
   for the eventual migration but deferred out of *this* slice (the spec's
   `status: undecided` out-of-slice surfaces: `m_summit`, `pick`, `CALENDAR`). They
   become tracked forward work (a `to-issues` follow-up or a `to-prd` PRD) instead of
   a silent drop.
3. **Generalization-review** — generic Oracle Forms affordances the ruleset derives
   *into a form's view* (block-CRUD record actions, the Execute-Query filter bar, the
   `ORA_ROWSCN` commit path, the `CLIENT_IDENTIFIER` bridge) that belong in a reusable
   layer, not in `OrdersView`. This applies PLAN.md's sort **proactively at design
   time** — a candidate captured before it is ever hand-fixed — the entry point the
   reactive fix-time sort lacked.

## Considered options

- **Fold review into the planner / build tickets.** Rejected: a `ready-for-agent`
  ticket promises an AFK agent can finish it; these deliberately cannot be finished
  without a human, so conflating them corrupts the `ready-for-agent` contract.
- **Reuse `.out-of-scope/` for deferred surfaces.** Rejected: that KB records
  *rejected* features for dedup; a deferred surface is *accepted but not now*. Mixing
  them poisons rejection dedup with false rejections (see `triage/OUT-OF-SCOPE.md`).
  Deferred and rejected are kept as **distinct lanes**; a human makes the
  defer-vs-reject call.
- **Encode generalization candidates as `migration-rules.yaml` markers.** Deferred,
  not rejected: the skill derives candidates from existing ruleset/spec signals for
  now. A `generalizesTo` hint on the generic classifiers is a possible future
  enhancement if the derivation proves too implicit.

## Consequences

- All review output is `ready-for-human`; the `ready-for-agent` build tickets are
  unchanged. Careful-review feeds the scorecard's *Behavioral parity* column;
  generalization decisions feed the *generalized-vs-specific* ratio — the POC's
  headline number — capturing a generalizable fix *before* it is hand-fixed.
- The "nothing dropped silently" guarantee now extends to the slice boundary, not
  just the form-object census.
