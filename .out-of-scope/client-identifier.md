# CLIENT_IDENTIFIER / Oracle proxy-auth bridge

This project does not build the `CLIENT_IDENTIFIER` (or Oracle proxy-authentication)
bridge that carries the real end-user identity through a pooled service-account
connection to the database.

## Why this is out of scope

The bridge only earns its keep when a DB-side object **reads the actor** — e.g. an audit
trigger calling `SYS_CONTEXT('USERENV','CLIENT_IDENTIFIER')` or `USER`. The Summit
`ORDERS` slice's only such object is the `AUDIT_S_ITEM` trigger, and it captures **no
actor** — its body is `INSERT INTO s_item_audit VALUES (1);`. With nothing in the slice
reading the identity, a plain HikariCP pool under one service account is sufficient, and
setting `CLIENT_IDENTIFIER` per request would be dead code.

This is a **drop for this slice on the merits**, not a deferral: there is no accepted
future capability behind it here. Oracle proxy auth / `CLIENT_IDENTIFIER` remain valid
patterns and should be reconsidered **if and when** a migrated form depends on a trigger
or view that actually reads the connected actor.

## Prior requests

- Derived from PRD #38 (v2) "Out of Scope" and `docs/orders-form-spec.md` audit-trigger
  finding during migration-review of the ORDERS slice.
