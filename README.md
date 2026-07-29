# Summit Migration POC — Java application

A Vaadin 25 + Spring Boot 4 application that connects to an Oracle database and
renders a single datum (the Oracle version banner) through a fully layered
architecture. It is the runnable scaffold for the Oracle Forms → Vaadin migration
proof of concept; the wider method and findings live in [`PLAN.md`](PLAN.md) and
[`CONTEXT.md`](CONTEXT.md), and a developer's guide to migrating one form end to end
(`.fmb` → analyze → PRD → issues → implement → review) is in
[`docs/migration-process.md`](docs/migration-process.md).

The demo view exists to prove the whole stack wires together — Vaadin, Spring,
HikariCP, the Oracle JDBC driver, and the `View ← Presenter ← Service ← Domain ← Data`
layering — before any real Forms construct is migrated onto it.

## Tech stack

| | Version |
|---|---|
| Java | 21 |
| Build | Maven (`pom.xml`) |
| Spring Boot | 4.1.0 |
| Vaadin | 25.2.0 |
| Database | Oracle Free (via Docker; `compose.yaml`) |
| JDBC driver | `com.oracle.database.jdbc:ojdbc17` |
| Data access | Spring `JdbcClient` |

## Architecture

The one demo datum is deliberately routed through **every** layer, so each seam is
exercised — not stubbed. Each class is a placeholder for the job it will do once the
real master-detail construct lands.

```
DemoView            ui/DemoView.java            @Route(""), renders one Span
  → DemoPresenter   presenter/DemoPresenter.java orchestration (load + label)
    → DemoService   service/DemoService.java     @Transactional boundary (load/commit)
      → ServerInfoRepository  data/ServerInfoRepository.java  JdbcClient SELECT
      ← Oracle
    ← ServerInfoFormula  domain/ServerInfoFormula.java  pure, unit-tested formula
  ← "Oracle AI Database 26ai Free (23.26.2.0.0)"
```

| Layer | Demo does | Real POC will do |
|---|---|---|
| `data` | one `JdbcClient` SELECT | lazy-paged `S_ORD` query + `ORA_ROWSCN` read |
| `service` | `@Transactional(readOnly)` | whole-graph save under `ORA_ROWSCN` (Forms `COMMIT_FORM`) |
| `domain` | trivial pure label fn + unit test | `recalculateTotal()`, `getExtendedPrice()` (see [ADR-0001](docs/adr/0001-business-logic-in-domain-layer.md)) |
| `presenter` | one `load()` call | Grid Pro cell-commit → recalc → push to view |
| `ui` | one `Span` | master Grid + detail Grid Pro + filter bar + LookupField |

Reactivity is via Vaadin `Binder` and listeners, not Signals — see
[ADR-0002](docs/adr/0002-binder-validation-no-signals.md).

## Prerequisites

- **Java 21** (`java -version`)
- **Maven 3.8+** (`mvn -version`)
- **Docker** with Compose (for the Oracle database)

## Quick start

```bash
# 1. Start Oracle (first boot takes ~1–2 min to initialize; wait for "healthy")
docker compose up -d
docker compose ps          # STATUS should read: Up (healthy)

# 2. Load the Summit schema + data (see "Loading the data" below) — one time

# 3. Run the application
mvn spring-boot:run

# 4. Open the demo view
open http://localhost:8080
```

The page shows the connected Oracle product and version, read live through all five
layers.

## Loading the data

The Summit dataset is a full schema export at
[`forms-summit-example/init-summit.sql`](forms-summit-example/init-summit.sql)
(75 tables, 15 sequences, ~54k rows). Two adjustments are required before it loads
cleanly into Oracle Free:

- It is qualified to the `SUMMIT` schema, so it must be loaded **as the app user
  `summit`** (Oracle uppercases that to schema `SUMMIT`).
- Its `CREATE TABLE`s hardcode `TABLESPACE "SYSTEM"`, where the app user has no quota.
  Strip that clause so objects default to the `USERS` tablespace.

```bash
sed -E 's/TABLESPACE +"SYSTEM"//g' forms-summit-example/init-summit.sql > /tmp/init-summit.users.sql

docker exec -i summit-oracle bash -lc \
  'echo "GRANT CREATE VIEW TO summit;" | sqlplus -s system/summit_sys@localhost:1521/FREEPDB1'

docker cp /tmp/init-summit.users.sql summit-oracle:/tmp/init-summit.users.sql
docker exec -i summit-oracle bash -lc \
  'sqlplus -s summit/summit@localhost:1521/FREEPDB1 @/tmp/init-summit.users.sql'
```

A clean load leaves ~84 residual errors on exotic objects the POC does not use (types
with dependents, CLOB-column tables); every POC slice table loads intact:

| Table | Rows |
|---|---|
| `S_ORD` (master) | 3,725 |
| `S_ITEM` (detail) | 12,509 |
| `S_PRODUCT` (LOV source) | 33 |
| `S_CUSTOMER` | 15 |
| `S_INVENTORY` (sub-detail) | 114 |

The `AUDIT_S_ITEM` trigger loads ENABLED.

## Configuration

Datasource and Vaadin settings are in
[`src/main/resources/application.properties`](src/main/resources/application.properties):

```properties
spring.datasource.url=jdbc:oracle:thin:@localhost:1521/FREEPDB1
spring.datasource.username=summit
spring.datasource.password=summit
spring.datasource.hikari.maximum-pool-size=5
```

The container's credentials are set in [`compose.yaml`](compose.yaml)
(`SYSTEM`/`summit_sys`, app user `summit`/`summit`, service `FREEPDB1`).

> Note: the `gvenzl/oracle-free:23-slim` image currently serves **Oracle AI Database
> 26ai Free**. The version query matches on `LIKE '%Database%'` so it is independent
> of the exact edition name.

## Tests

```bash
mvn test
```

`ServerInfoFormulaTest` unit-tests the Domain layer in isolation — no Spring context,
no database — which is the property the layering exists to buy (see ADR-0001).

## Production build

```bash
mvn -Pproduction clean package
java -jar target/summit-migration-poc-0.0.1-SNAPSHOT.jar
```

The `production` profile compiles the optimized Vaadin frontend bundle and excludes
the dev server. (In development, the `vaadin-dev` dependency is supplied by an
`activeByDefault` Maven profile, since Vaadin 25 no longer pulls it transitively via
`vaadin-core`.)

## Project layout

```
pom.xml                         Maven build (Spring Boot parent, Vaadin BOM)
compose.yaml                    Oracle Free 23ai container
src/main/java/com/example/summit/
  Application.java              Spring Boot entry point
  ui/MainLayout.java            AppLayout shell
  ui/DemoView.java              @Route(""), the demo view
  presenter/DemoPresenter.java  orchestration
  service/DemoService.java      transactional boundary
  domain/ServerInfo.java        domain record
  domain/ServerInfoFormula.java pure formula
  data/ServerInfoRepository.java JdbcClient query
src/main/resources/
  application.properties        datasource + Vaadin config
src/test/java/com/example/summit/
  domain/ServerInfoFormulaTest.java
docs/                           ADRs, migration ruleset, form spec
forms-summit-example/           Oracle Forms source + Summit data dump
```
