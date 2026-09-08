# PROJECT_STATE.md

**Ethanova — Canonical Project State Document**
*Last updated: 8 September 2026*
*Purpose: Single source of truth for the Ethanova project. Any new chat session or engineer can pick up from this document alone.*

---

## 1. Project Overview

| Field | Value |
|---|---|
| **Project name** | Ethanova |
| **Full title** | An Enterprise Decision Intelligence Platform for India's E20 Biofuel Supply Chain |
| **Type** | MCA Final Year Major Project |
| **Objective** | Build a warehouse-native decision intelligence platform on top of an operational Spring Boot system, using medallion architecture (Bronze/Silver/Gold), Airflow orchestration, Power BI dashboards, and rule-based recommendations. Focused on one hero decision: *"Should the Regional Procurement & Blending Planner order more ethanol from a specific supplier for a specific depot next week?"* |
| **Team structure** | 5 members on paper; effectively solo development by Kushagra Saini (tech lead, all tracks). |
| **GitHub** | https://github.com/Kus1hagra-Saini/ethanova (public) |
| **Local path** | `D:\MCA\E20_SupplyChain\ethanova\` |
| **License** | MIT |

### Review timeline

| Review | Window | Status |
|---|---|---|
| Zeroth | 21–24 Jul 2026 | ✅ Completed |
| First | 18–21 Aug 2026 | ⚠️ Missed |
| **Second** | **8–11 Sep 2026** | **Current target — 9 Sep** |
| Third | 29 Sep – 2 Oct 2026 | Not started |

---

## 2. Technology Stack

| Layer | Technology | Version | Notes |
|---|---|---|---|
| **Backend framework** | Spring Boot | **4.0.7** | Deliberately kept on 4.x — momentum over ecosystem-familiarity concerns |
| **Language (backend)** | Java | 21 source/target, runs on 24 | "Compile for LTS, run on latest" pattern |
| **Build** | Maven | 3.9.16 | Wrapper checked in (`mvnw.cmd`) |
| **ORM** | Hibernate via Spring Data JPA | Bundled with Boot 4 | `ddl-auto=validate`, `open-in-view=false` |
| **OLTP + Warehouse** | PostgreSQL | 16-alpine | Single Postgres instance, multiple schemas |
| **Migrations** | Flyway (via `spring-boot-starter-flyway`) | 11.x (managed by Boot BOM) | Must use the starter — see §9 ADR #18 |
| **API documentation** | SpringDoc OpenAPI | **3.1.0** | Spring Boot 4 / Jackson 3 compatible line — see §9 ADR #24 |
| **Language (data)** | Python | 3.11.9 | Alongside 3.14 via `py -3.11` launcher |
| **Orchestrator** | Apache Airflow | 2.10.x (planned) | LocalExecutor, ~4 DAGs, Docker-only |
| **BI** | Power BI | Latest desktop | Primary; Superset possible future scope |
| **ML libraries** | scikit-learn, Prophet | Latest | Only 2 models planned (forecast + anomaly) |
| **Containerisation** | Docker Desktop + WSL2 | 29.6.2, Compose v5.3.1 | 7.6 GB RAM allocated |
| **Version control** | Git + GitHub | 2.53 | `main` branch, feature branches for changes |

---

## 3. Repository Structure

```
ethanova/
├── backend/                              # Spring Boot 4.0.7 — active
│   ├── src/main/java/com/ethanova/backend/
│   │   ├── BackendApplication.java
│   │   ├── common/
│   │   │   ├── audit/                    # BaseAuditableEntity, JpaAuditingConfig (OffsetDateTime provider)
│   │   │   ├── config/                   # SecurityConfig, OpenApiConfig
│   │   │   └── exception/                # ResourceNotFoundException, ApiError, GlobalExceptionHandler
│   │   ├── identity/                     # User, UserRole enum, UserRepository
│   │   ├── masterdata/                   # Supplier, ProductionPlant, Depot + enums + repos + services + controllers + DTOs
│   │   │   └── dto/                      # SupplierRequest/Response, DepotRequest/Response
│   │   ├── inventory/                    # Inventory + repo + service + controller
│   │   │   └── dto/                      # InventoryResponse (read-only)
│   │   └── dispatch/                     # DispatchOrder, DispatchStatus (with canTransitionTo), EthanolGrade
│   │       └── dto/                      # DispatchOrderCreateRequest, StatusUpdateRequest, Response
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-local.yml
│   │   └── db/migration/
│   │       ├── V001__create_operational_schema.sql
│   │       ├── V002__seed_reference_data.sql
│   │       └── V003__create_depot_weekly_consumption.sql
│   ├── pom.xml
│   └── mvnw.cmd, .mvn/, etc.
├── data-platform/                        # empty — awaiting Airflow scaffold (Milestone 5)
├── simulator/                            # Python 3.11 historical data simulator (M4) ✅
│   ├── pyproject.toml
│   ├── README.md
│   ├── .gitignore
│   ├── src/ethanova_simulator/
│   │   ├── __init__.py, __main__.py, cli.py
│   │   ├── config.py, db.py
│   │   └── generators/
│   │       ├── consumption.py
│   │       └── dispatch.py
│   └── tests/test_generators.py
├── deployment/
│   ├── docker-compose.yml                # PostgreSQL 16-alpine service
│   ├── .env.example
│   ├── .env                              # gitignored
│   └── postgres/init/01-schemas.sql      # creates operational/bronze/silver/gold on first init
├── docs/                                 # PROJECT_STATE.md + future reviews/
├── scripts/                              # empty
├── .gitignore
├── .gitattributes
├── .editorconfig
├── LICENSE                               # MIT
└── README.md
```

**Deferred folders (add when the trigger fires, not before):**

| Trigger | Folder to add | Rationale |
|---|---|---|
| Phase 2 warehouse DDL work | `data-platform/warehouse/` | Warehouse is a data-platform output |
| Second Review (Power BI + ML) | `analytics/` at root | Top-level, downstream consumer |
| First CI workflow | `.github/workflows/` | Only when Actions are configured |
| React portal (Phase 3) | `frontend/` at root | Independent runtime |

---

## 4. Completed Milestones

### Milestone 0 — Zeroth Review (21–24 Jul 2026)
Formal project approval. Two deliverables produced and approved:
- `Ethanova_Zeroth_Review_Proposal.docx` — 17-page proposal
- `Ethanova_Zeroth_Review.pptx` — 10-slide presentation with speaker notes

### Milestone 1 — Phase 1 Foundation (28–30 Jul 2026)
Environment audit, monorepo initialisation, GitHub publication, Spring Boot scaffold, Docker Compose + PostgreSQL, backend ↔ PostgreSQL wiring. All merged via feature branches.

### Milestone 2 — Operational Backend Foundation (30 Jul – 4 Aug 2026)
Two Flyway migrations (V001 schema, V002 seed), 6 JPA entities + `BaseAuditableEntity`, 6 Spring Data JPA repositories with Review-1-focused query methods. Flyway starter fix documented as ADR #18.

### Milestone 3 — REST API Layer (4–5 Aug 2026)

Delivered on `feature/rest-api` (PR #4). Complete Review-1-ready REST surface for the four operational domains.

**M3.1 — Global exception infrastructure.** `ResourceNotFoundException`, `ApiError` record, `GlobalExceptionHandler` with four handlers: 404 (not found), 400 (bean validation), 409 (data integrity), 500 (generic sanitised). Handler set extended during 3.2–3.6 with: `HttpMessageNotReadableException` → 400, `MethodArgumentTypeMismatchException` → 400, `ResponseStatusException` → status-preserving.

**M3.2 — Supplier CRUD.** `SupplierRequest` / `SupplierResponse` records with Bean Validation, `SupplierService` (transactional, inline mapping), `SupplierController` on `/api/v1/suppliers`. `existsBySupplierCode` added for targeted 409. During this milestone: `SecurityConfig` added to permit all requests for Review 1 (see §9 ADR #25).

**M3.3 — JPA auditing OffsetDateTime fix.** Custom `DateTimeProvider` bean supplied to `@EnableJpaAuditing` so `@CreatedDate`/`@LastModifiedDate` populate `OffsetDateTime` fields correctly. Documented as ADR #26.

**M3.4 — Depot CRUD.** Same shape as Supplier: `DepotRequest`/`Response`, `DepotService`, `DepotController` on `/api/v1/depots`. `existsByDepotCode` added.

**M3.5 — Inventory read-only API.** `InventoryResponse` DTO, `InventoryService` (read-only, JOIN FETCH depot to avoid N+1), `InventoryController` on `/api/v1/inventory` with three access patterns. `DepotRepository` used to distinguish Depot 404 from Inventory 404. Inventory writes are intentionally absent — the write path belongs to dispatch-order side effects (deferred).

**M3.6 — Dispatch Order API.** Three DTOs (`Create`, `StatusUpdate`, `Response`), `DispatchStatus` enum owns transition rules via `canTransitionTo()`, `DispatchOrderService` (FK resolution by business code, plant-supplier integrity check, server-generated order numbers `DO-YYYY-MM-NNNN`, computed `totalAmountInr`), `DispatchOrderController` on `/api/v1/dispatch-orders`. Repository extended with `existsByOrderNumber`, count-by-prefix, and JOIN FETCH variants. Inventory side-effect on `DELIVERED` transition is a documented extension point — not implemented (see §11).

**M3.7 — SpringDoc OpenAPI integration.** `springdoc-openapi-starter-webmvc-ui:3.1.0` added. `OpenApiConfig` supplies title/description/license. `@Tag` on all four controllers; `@Operation` summaries on every endpoint; `@ApiResponses` documenting 400/404/409 mapped to the `ApiError` schema; `@Schema` field descriptions on request DTOs. Swagger UI at `/swagger-ui.html`, raw spec at `/v3/api-docs`.

### Milestone 4 — Historical Data Simulator (8 Sep 2026)

Delivered on `feature/data-platform-m4` (two atomic commits landed; the accompanying docs commit for this PROJECT_STATE.md update is pending; branch unmerged at time of writing). Purpose: seed the operational schema with ~2 years of realistic weekly history so the Review 2 Bronze/Silver/Gold DAGs and baseline forecast have something to consume. Backend application code untouched.

**M4.1 — V003 migration.** New table `operational.depot_weekly_consumption` captures the weekly demand signal per depot: `petrol_dispensed_kl`, `target_blend_percentage`, `ethanol_required_kl` (the forecast target), and `ethanol_blended_kl` (actual). One row per (depot, ISO week), keyed by `UNIQUE (depot_id, week_start_date)`. Additive migration — no changes to existing tables. Mirrors V001 conventions (BIGSERIAL PK, `NUMERIC(15,3)` for volumes, `TIMESTAMPTZ` audit columns, `uk_`/`fk_`/`ix_` naming, `ON DELETE RESTRICT`, `COMMENT ON` documentation). Adds two `CHECK` constraints (ISO week range 1–53, blend percentage 0–100) and a `data_source VARCHAR(30) NOT NULL DEFAULT 'SIMULATOR'` provenance column. See §9 ADR #36.

**M4.2 — Simulator Python project.** Standalone `src`-layout project under `simulator/`, Python 3.11, setuptools + `pyproject.toml`, editable install with `[dev]` extra. Dependencies: SQLAlchemy 2.x, `psycopg2-binary`, `numpy`, `pandas`, `python-dotenv`, plus `pytest` and `matplotlib` in the dev extra. Reads DB credentials from `deployment/.env` via `python-dotenv`. Reference data (depots, suppliers, plants) is loaded at runtime and filtered to real rows only: codes matching `%TEST%` or `%-999` are excluded, and suppliers must have at least one active production plant. CLI entry point: `python -m ethanova_simulator generate [--weeks 104] [--seed 42] [--end-week YYYY-MM-DD] [--truncate] [--sanity-plot]`. Writes directly to Postgres via SQLAlchemy Core bulk inserts.

**M4.3 — Generation model.** Weekly petrol demand per depot composed of: per-depot base level (config-driven; ~10,000 KL/wk for Kanpur/Lucknow down to ~7,000 for Moradabad), ~3% secular annual growth, ±12% seasonal swing peaking near ISO week 44 (early November), festival multipliers for Diwali/Holi/Eid, and ~6% lognormal noise. Blend percentage ramps linearly from **10% to 20%** across the window, reflecting the E20 rollout — gives the forecaster a genuine non-seasonal trend to decompose. Supply shortfalls hit ~3% of weeks at 5–15%, producing weeks where `ethanol_blended_kl < ethanol_required_kl` for the downstream DQ story. Dispatch generator produces 2–3 orders per depot per week, allocated across suppliers weighted by `reliability_score` (uniform fallback when scores are null), with grade weights 90/7/3 (`ANHYDROUS`/`HYDROUS_95`/`DENATURED`), prices ramping from ~62.500 to ~72.000 INR/L, order numbers formatted `DO-YYYY-MM-NNNN` with per-month monotonic sequences, all historical rows set to `DELIVERED`.

**M4.4 — Determinism and testing.** A single `numpy.random.default_rng(seed)` threads through both generators. Three `pytest` smoke tests: same-seed determinism, consumption shape/range validation, and dispatch generation (uniqueness, positivity, grade domain, delivered status). No database access in tests. All three pass.

**Verified outputs of `generate --weeks 104 --seed 42 --truncate` against the live DB:**
- `operational.depot_weekly_consumption`: **520 rows** (5 real depots × 104 weeks).
- `operational.dispatch_orders`: ~1,250–1,300 rows across all 5 real suppliers.
- 104 distinct `week_start_date` values ending at the Monday of the current week.
- Blend percentages span 10.00 → 20.00 as designed.
- Shortfall weeks land near the ~3% target.
- Grade mix within ~1 percentage point of 90/7/3.
- Test-fixture rows (`SUP-UP-999`, `DEP-UP-TEST-001`) receive zero simulator-generated rows.
- Per-depot sanity plots under `simulator/verification/` (gitignored).

**Backend application code was not modified during M4.** The REST API remains the live write path for operational events; the simulator writes historical rows directly for pragmatic reasons — see §9 ADR #36 for the split-of-concerns rationale.

---

## 5. Current Backend Status

| Item | Status | Notes |
|---|---|---|
| Spring Boot project initialised | ✅ Complete | Boot 4.0.7, Java 21 target |
| Backend starts successfully | ✅ Complete | With Flyway starter, OffsetDateTime auditing, permissive SecurityConfig |
| PostgreSQL connected | ✅ Complete | Hikari pool `ethanova-hikari` on localhost:5432 |
| Flyway configured | ✅ Complete | Confined to `operational` schema; V001, V002, V003 applied |
| Operational schema created | ✅ Complete | 6 tables via V001; `depot_weekly_consumption` added by V003 |
| Seed data added | ✅ Complete | 3 users, 5 real suppliers + 1 test-fixture supplier, 6 plants, 5 real depots + 1 test-fixture depot, 5 inventory rows. Test fixtures (`SUP-UP-999`, `DEP-UP-TEST-001`) originated from REST-API testing and are filtered out by the simulator |
| JPA entities | ✅ Complete | 6 entities + `BaseAuditableEntity` |
| Repository layer | ✅ Complete | 6 repositories; extended in M3 with `existsBy*` and JOIN FETCH variants |
| **Service layer** | ✅ **Complete** | 4 services (Supplier, Depot, Inventory, DispatchOrder) |
| **REST controllers** | ✅ **Complete** | 4 controllers, ~17 endpoints total |
| **Global exception handling** | ✅ **Complete** | 6 handlers, consistent `ApiError` envelope |
| **API documentation** | ✅ **Complete** | SpringDoc + Swagger UI |
| Security / JWT | ⏳ Not started | Permissive `SecurityConfig` for Review 1; JWT deferred to Phase 2 |
| **`depot_weekly_consumption` table** | ✅ **Complete** | Added by V003; 520 rows after simulator run — weekly demand signal for the forecast pipeline |
| **Historical data simulator** | ✅ **Complete** | Python 3.11 project under `simulator/`; 104 weeks of history + ~1,300 dispatch orders; direct SQLAlchemy writes |
| Airflow project | ⏳ Not started | Milestone 5 (next) |
| Bronze / Silver / Gold DAGs | ⏳ Not started | Milestone 6+ |
| ML baseline forecast | ⏳ Not started | Post-Airflow milestone |
| Power BI dashboard | ⏳ Not started | Post-forecast milestone |

**Endpoint inventory as of Milestone 3 completion:**

| Domain | Endpoints |
|---|---|
| Suppliers | `GET /api/v1/suppliers`, `GET /{code}`, `POST`, `PUT /{code}`, `DELETE /{code}` |
| Depots | `GET /api/v1/depots`, `GET /{code}`, `POST`, `PUT /{code}`, `DELETE /{code}` |
| Inventory | `GET /api/v1/inventory`, `GET /depot/{code}`, `GET /depot/{code}/grade/{grade}` |
| Dispatch Orders | `GET /api/v1/dispatch-orders`, `GET /{orderNumber}`, `POST`, `PUT /{orderNumber}/status` |
| Actuator | `GET /actuator/health`, `GET /actuator/info` |
| Docs | `GET /swagger-ui.html`, `GET /v3/api-docs` |

**Error taxonomy:**

| Status | Trigger |
|---|---|
| 400 | Bean validation, malformed JSON, invalid enum path variable, business-rule failures (date range, plant/supplier mismatch) |
| 404 | Missing resource (any domain) |
| 409 | Duplicate business codes, FK integrity violations, invalid status transitions |
| 500 | Sanitised message; full details logged, never returned |

---

## 6. Database Status

**Schemas** (created by `deployment/postgres/init/01-schemas.sql`):

| Schema | Purpose | Ownership |
|---|---|---|
| `operational` | Spring Boot OLTP tables — source of truth for business events | Backend |
| `bronze` | Raw ingested events, immutable | Data platform (empty) |
| `silver` | Cleansed, validated, deduplicated | Data platform (empty) |
| `gold` | Business-ready star schema | Data platform (empty) |

**Migrations applied:**

| File | Purpose | Status |
|---|---|---|
| `V001__create_operational_schema.sql` | 6 tables with constraints, indexes, comments | Applied |
| `V002__seed_reference_data.sql` | Reference data | Applied |
| `V003__create_depot_weekly_consumption.sql` | Weekly demand-signal table; one row per (depot, ISO week) | Applied |

**Tables in `operational` schema (post-M4):**

| Table | Rows | Notes |
|---|---|---|
| `users` | 3 | admin, planner, depot_mgr |
| `suppliers` | 5 real + 1 fixture | Real: `SUP-UP-001` … `SUP-UP-005`. Fixture: `SUP-UP-999 Test Supplier` (created via REST-API testing; filtered out by the simulator) |
| `production_plants` | 6 | `PLT-UP-001` … `PLT-UP-006` |
| `depots` | 5 real + 1 fixture | Real: `DEP-UP-KNP` (Kanpur), `DEP-UP-LKO` (Lucknow), `DEP-UP-VNS` (Varanasi), `DEP-UP-AGR` (Agra), `DEP-UP-MDB` (Moradabad). Fixture: `DEP-UP-TEST-001` (filtered out by the simulator) |
| `dispatch_orders` | ~1,250–1,300 after simulator run | Historical rows all `DELIVERED`; simulator uses the same `DO-YYYY-MM-NNNN` format as the REST API |
| `inventory` | 5 | One row per depot × ANHYDROUS grade. Simulator does **not** write to this table — inventory is state, not event history |
| `depot_weekly_consumption` | 520 after simulator run | One row per (depot, ISO week); demand signal for the forecast pipeline; `data_source = 'SIMULATOR'` |
| `flyway_schema_history` | 3 | V001, V002, V003 applied |

---

## 7. Git History Summary

**Branching model:** `main` protected in convention; all work via `feature/*` branches → PR → merge into `main`.

**Completed and merged into `main`:**

| Milestone | Feature branch | PR | What it delivered |
|---|---|---|---|
| Repository initialisation | (direct on main) | — | Root config files |
| Spring Boot scaffold | `feature/backend-scaffold` | ✅ | `backend/` from Spring Initializr |
| Docker Compose + PostgreSQL | `feature/docker-postgres` | ✅ | `deployment/docker-compose.yml`, schemas init |
| Backend DataSource wiring | `feature/backend-datasource` | ✅ | `application.yml` + Hikari config |
| Operational schema + entities | `feature/operational-schema` | #2 | Milestone 2 |
| Milestone 3 — REST API + OpenAPI | `feature/rest-api` | #4 | Full REST surface + Swagger UI |

**Merged PR #4 contained five commits:**
1. `feat(backend): add global exception handling infrastructure`
2. `fix(backend): configure OffsetDateTime JPA auditing`
3. `feat(backend): add Depot REST API with full CRUD`
4. `feat(backend): add Inventory read-only REST API`
5. `feat(backend): complete REST API for suppliers, dispatch orders, and OpenAPI docs`

The last commit is a consolidated final commit covering Supplier CRUD, Dispatch Order, additional exception handlers, and the SpringDoc pass — a granularity trade-off consciously accepted (§10 rule 5).

**Current branch — Milestone 4 (unmerged):**

Branch: `feature/data-platform-m4`, forked from `main` at `c0352b0` (the merge commit of PR #4). Two atomic commits landed so far, not yet pushed to a PR:

1. `911fdc2` — `feat(backend): add V003 migration for depot_weekly_consumption`
2. `bc167fb` — `feat(simulator): scaffold historical data simulator (M4)`

A third commit updating this PROJECT_STATE.md file for M4 completion is pending review at time of writing; it will be added and the branch pushed once the doc changes are approved. `main` still points at `c0352b0`; no M4 work has been merged.

**Conventions:**
- Commit messages follow Conventional Commits: `type(scope): summary` + body bullets
- Types used: `feat`, `fix`, `chore`, `docs`, `refactor`
- Scopes used: `backend`, `deployment`, `docs`
- All migration files land under `backend/src/main/resources/db/migration/`
- `.env` is gitignored; `.env.example` is committed

---

## 8. Remaining Work for Review 2 (9 Sep 2026)

Review 1 was missed; Review 2 is tomorrow. Historical dataset (M4) is now in place and the operational schema carries a first-class demand signal. The remaining work is the analytical pipeline that consumes it. Ordered by dependency and priority.

### Already delivered (referenced here so the plan reads honestly)

- ✅ Operational REST API + OpenAPI (M3, merged in #4).
- ✅ Historical data simulator + V003 demand-signal table (M4, current branch).
- ✅ 520 weekly consumption rows and ~1,300 dispatch orders now sitting in `operational`, ready for extraction.

### Tier 1 — Must-have for Review 2 demo

1. **[Data platform] Airflow in Docker Compose** — Extend `deployment/docker-compose.yml` with Airflow 2.10, LocalExecutor, webserver on port 8081. Reuse the existing Postgres container for Airflow metadata via a separate database. Bind-mount `data-platform/dags/`. **Milestone 5 (next).** Hard timebox: 4h. Venv fallback pre-approved if Docker Airflow becomes a blocker.

2. **[Pipeline] Bronze DAG — `extract_operational_to_bronze`** — Reads `dispatch_orders`, `inventory`, and `depot_weekly_consumption` from `operational` using an `updated_at` watermark; writes to `bronze.dispatch_orders_raw`, `bronze.inventory_snapshot_raw`, `bronze.depot_consumption_raw` with an `_ingested_at` timestamp. **Milestone 6.**

3. **[Pipeline] Silver DAG — `bronze_to_silver`** — Deduplicate, type-cast, join with dimensions, add data-quality flags. Produces `silver.dispatch_orders_clean` and `silver.depot_weekly_demand`. Simulator's ~3% supply-shortfall weeks provide real DQ signal to flag. **Milestone 7.**

4. **[Pipeline] Gold DAG — `silver_to_gold`** — Star-schema-lite: `dim_depot`, `dim_supplier`, `dim_date`, `fact_weekly_ethanol_demand`, `fact_weekly_dispatched_volume`. Weekly grain, ready for BI and ML. **Milestone 8.**

5. **[ML] Baseline forecast** — Notebook under `analytics/notebooks/` reading `gold.fact_weekly_ethanol_demand`. Time-based train/test split with last 8 weeks held out per depot. Two models: seasonal-naive (same week last year) and Ridge regression on lag + week-of-year features. Metrics per depot: MAE, MAPE. Save per-depot forecasts to `gold.forecast_weekly`. **Milestone 9.**

6. **[Decision] Rule-based recommendation output** — `gold.recommendation_weekly` populated by SQL view or Python step: `depot × grade × next_week` → `recommended_order_kl`, `chosen_supplier_code`, `rationale`. Formula: `forecast_next_week - opening_inventory + safety_stock`; supplier chosen by cheapest active supplier with capacity. Consistent with §9 ADR #16 (rule-based, not ML policy). **Milestone 10.**

7. **[Docs] Review 2 presentation deck** — 12–15 slides: architecture as-built, medallion story, Airflow screenshots, sample Bronze/Silver/Gold rows, forecast plots, recommendation table, honest limitations slide. **Milestone 12.**

### Tier 2 — Nice-to-have, adds credibility if time permits

8. **[BI] Power BI dashboard** — Desktop, one page: weekly demand by depot, forecast vs actual, recommendation table. Connects to `gold` via Npgsql. Jupyter/matplotlib fallback prepared in parallel. **Milestone 11.**
9. **[Pipeline] Data quality validation** in the Bronze DAG — record count reconciliation and DQ-flag surface between operational and Bronze.
10. **[Backend] Actuator info endpoint** enriched with build metadata (Git commit, build time).
11. **[Docs] Architecture Decision Records (ADRs)** — start `docs/adr/` with 4–5 real ADRs extracted from §9.

### Tier 3 — Explicitly deferred past Review 2

- Prophet as a second forecast model
- Anomaly detection ML model
- Silver/Gold slowly-changing-dimension logic beyond append
- Full RBAC + JWT
- Real-time streaming or CDC
- MLflow, hyperparameter tuning, model registry

---

## 9. Important Architectural Decisions

Locked decisions. Do not re-open without explicit instruction from Kushagra.

| # | Decision | Rationale |
|---|---|---|
| 1 | Monorepo | Cross-cutting changes are the norm |
| 2 | MIT License | Portfolio project; standard permissive |
| 3 | `main` as default branch | Modern convention |
| 4 | Feature branches + PRs even for solo work | Discipline + reviewable history |
| 5 | Java 24 runtime, Java 21 source/target | "Compile for LTS, run on latest" |
| 6 | Python 3.11 for all data work | Airflow 2.10 supports 3.8–3.12 |
| 7 | Spring Boot 4.0.7 kept (not downgraded to 3.5.x) | Working scaffold; ecosystem friction acceptable |
| 8 | DataSource introduced only after PostgreSQL exists | Cleanest sequencing |
| 9 | `application.yml` over `.properties` | Modern default |
| 10 | Hibernate `ddl-auto=validate` | Schema changes only via migrations |
| 11 | `open-in-view=false` | Prevents N+1 anti-pattern |
| 12 | Single Postgres instance with schemas | Logical separation without operational overhead |
| 13 | Airflow in Docker only, LocalExecutor, ~4 DAGs | Realism without complexity inflation |
| 14 | Power BI as primary BI | Matches OMC/consulting placement pool |
| 15 | Two ML models max (forecasting + anomaly) | Depth over breadth |
| 16 | Rule-based recommendations (not ML policy) | Auditability for regulated supply chain |
| 17 | First-class data simulator calibrated to PPAC/NITI Aayog | Deliverable, not workaround |
| 18 | `spring-boot-starter-flyway` (not raw `flyway-core`) on Boot 4.x | Boot 4.0 modularised auto-config |
| 19 | Milestone 2 scope reduced to 6 entities | Every entity actively used in Review 1 |
| 20 | `BaseAuditableEntity` with `createdAt`/`updatedAt`/`@Version` only | `created_by`/`updated_by` deferred until Spring Security integration |
| 21 | Enums always `@Enumerated(EnumType.STRING)` | Ordinal is unsafe when enums evolve |
| 22 | BIGSERIAL primary keys, business codes as unique varchars | Fast joins, human-readable references |
| 23 | Volumes `NUMERIC(15,3)` KL, money `NUMERIC(15,2)` INR | Never FLOAT/DOUBLE for money or measured quantities |
| **24** | **SpringDoc `3.x` line (`3.1.0`) for OpenAPI on Spring Boot 4** | SpringDoc versions its major line to match Spring Boot's; `2.x` is Boot 3, `3.x` is Boot 4. `3.0.1` shipped before the Jackson 3 fix; `3.1.0` is the safe minimum |
| **25** | **Permissive `SecurityConfig` for Review 1 (no auth)** | `spring-boot-starter-security` stays on the classpath for stable integration point. Real JWT flow arrives in Phase 2 without touching any controller |
| **26** | **Custom `DateTimeProvider` returning UTC `OffsetDateTime` for JPA auditing** | Default provider returns `LocalDateTime`, which cannot be assigned to `OffsetDateTime` audit fields. UTC anchoring keeps timestamps unambiguous across JVM/DB timezones |
| **27** | **Business-key URLs for every REST resource** (`supplierCode`, `depotCode`, `orderNumber`) | Surrogate IDs are DB-internal. Business codes are stable, human-readable, and safe to share across integrations |
| **28** | **Java records for every DTO; inline static mapping inside services** | Records are immutable and Jackson-friendly. Inline mapping avoids MapStruct's build tax at current DTO count. Revisit MapStruct if DTO count exceeds ~15 |
| **29** | **`DispatchStatus.canTransitionTo()` — status lifecycle owned by the enum** | Domain rule co-located with the domain type; single point of truth for viva and audit |
| **30** | **Server-generated order numbers `DO-YYYY-MM-NNNN`, monthly sequence** | Auditable, sortable, race-safe under Review 1 volumes (DB unique constraint is the safety net) |
| **31** | **Server-computed `totalAmountInr`; client cannot override** | Prevents drift; the definition of total is `quantity × price` |
| **32** | **JOIN FETCH on read endpoints exposing FK data (Inventory, Dispatch Order lists)** | Single-query load path; N+1 avoided declaratively via `@Query` |
| **33** | **Consistent `ApiError` envelope on every failure path** | Predictable client experience; documented in OpenAPI so every 400/404/409 points at the same schema |
| **34** | **PUT (not PATCH) for dispatch-order status transitions, for Review 1** | Simpler surface for demo and viva; PATCH is a defensible refinement post-Review 1 |
| **35** | **Cross-field validation lives in services, not on DTOs** | Bean Validation's `@AssertTrue` on records is awkward and couples the DTO to business rules; imperative checks in the service are clearer |
| **36** | **Weekly demand signal lives in `operational.depot_weekly_consumption` as a first-class table, not derived downstream** | Forecasting off historical dispatch decisions would be circular — dispatches are the supply-side response, not the demand signal. An explicit demand table (petrol dispensed × blend target) gives Silver/Gold a clean, provenance-tracked target column via `ethanol_required_kl`. The row *is* the aggregate; the simulator populates it today, a real OMC feed populates it tomorrow, without pipeline changes. The `data_source` column carries provenance for downstream filtering. Corollary: the simulator writes historical rows directly via SQLAlchemy, not through the REST API — historical seeding is a separate concern from the live write path |

---

## 10. Rules for Future Sessions

Non-negotiable operating principles for any chat session continuing this project.

1. **Never regenerate completed work.** If a milestone in §4 is marked ✅, the code exists and is committed. Do not rescaffold, do not rewrite unless explicitly asked.

2. **Always continue from PROJECT_STATE.md.** This document is the source of truth. Chat history is not. Read this file at the start of every session.

3. **Ask before making major architectural changes.** The 36 locked decisions in §9 are frozen. If a session recommends changing any of them, halt and ask Kushagra first.

4. **Keep implementation enterprise-grade but avoid unnecessary complexity.** No microservices, no Kafka, no cloud, no premature abstractions. Every class must earn its place in Review 1.

5. **Work milestone by milestone. Commit atomically when the milestone completes.** Milestone 3 taught us that "commit at the end" needs to be enforced explicitly per sub-milestone, or intermediate work risks piling up into a single large commit. The recovery (one consolidated commit + honest PR description) is legitimate but avoidable.

6. **Mentoring style: senior-engineer pairing with a junior developer.**
   - Short explanations (2–4 sentences) for routine tasks.
   - Detailed architectural reasoning reserved for real design decisions.
   - No textbook essays for configuration files.

7. **Prefer generated/templated over hand-written boilerplate.** Spring Initializr over manual `pom.xml`, official Docker images over hand-built, framework defaults over custom configuration.

8. **Never assert a technical fix without evidence.** If a claim depends on framework internals or version behaviour, verify with current documentation or Maven Central. ADR #18 (Flyway starter), ADR #24 (SpringDoc line), and ADR #26 (DateTimeProvider) are the reference cases.

9. **Update PROJECT_STATE.md immediately when a milestone completes.** Overwrite §4, §5, §6, §7, §8, §9, §11 as appropriate. Bump the "Last updated" date at the top.

10. **Kushagra's judgement overrides Claude's defaults.** He is a senior full-stack developer. If he pushes back on a recommendation, the pushback is likely correct — verify reasoning before defending.

11. **When verifying via REST/curl/PowerShell, always use real seed codes.** Don't invent depot or plant codes in verification snippets. The M3.6 debugging session lost time to a placeholder mismatch that could have been avoided by checking against the DB first.

---

## 11. Deferred Items (Post-Review-1)

Explicitly deferred with rationale. Not to be revived without Kushagra's approval.

| # | Item | Where | Why deferred |
|---|---|---|---|
| 1 | Split `SupplierRequest` / `DepotRequest` into `*CreateRequest` and `*UpdateRequest` DTOs | Both master-data DTOs | On PUT, the URL identifier is authoritative and the body's copy is ignored. Currently the client must include the identifier in the PUT body to pass validation. Cleaner split noted, not urgent |
| 2 | Inventory side-effect on `DispatchStatus.DELIVERED` transition | `DispatchOrderService.updateStatus` | Documented extension point exists. Requires idempotency, capacity checks, and coordinated transaction scope with `InventoryRepository`. Out of scope for Review 1 |
| 3 | JWT / Spring Security auth flow | `common/config/SecurityConfig` | Permissive config for Review 1; real auth is Phase 2 |
| 4 | Client-provided order numbers | `DispatchOrderService.create` | Currently server-generated only. If the simulator ever needs deterministic re-runs, we may revisit |
| 5 | Pagination on list endpoints | All controllers | Review 1 volumes are tiny; `Pageable` triples DTO complexity without benefit |
| 6 | PATCH for partial updates | Supplier, Depot, Dispatch Order | PUT is the Review 1 verb. PATCH is a defensible refinement later |
| 7 | Silencing the "Using generated security password" log line | `application.yml` or `BackendApplication` | Cosmetic. The password is never usable (no filter consumes it) |
| 8 | MapStruct for DTO mapping | All services | Inline mapping is fine at current DTO count (~10). Revisit if we exceed ~15 |

---

## 12. Session Notes

**Milestone 3 debugging highlights (kept as learning artefacts):**

- **PowerShell `curl.exe -d '...'` strips inner quotes** on native-exe interop. Any future POST/PUT test should use `Invoke-RestMethod` or `curl.exe --data @file.json`. Documented in the M3.2 and M3.6 sessions.
- **`ResponseStatusException` needs an explicit handler** in `@RestControllerAdvice` if a catch-all `handleGeneric` exists — the generic handler wins by pattern-match order otherwise. This was the M3.6 400/409 → 500 issue.
- **JPA auditing default provider (`CurrentDateTimeProvider`) returns `LocalDateTime`** — not assignment-compatible with `OffsetDateTime` fields. Fix: bean-name-referenced custom `DateTimeProvider`. ADR #26.
- **SpringDoc's major version tracks Spring Boot's major version.** `2.x` = Boot 3.x, `3.x` = Boot 4.x. ADR #24.

**Milestone 4 highlights (kept as learning artefacts):**

- **Test-fixture rows are real data too.** `SUP-UP-999` and `DEP-UP-TEST-001` were created by REST-API testing in M3 and are legitimately present in `operational`. The simulator filters them out via `code NOT LIKE '%TEST%' AND code NOT LIKE '%-999'` plus a "supplier must have at least one active plant" clause — rather than deleting the fixtures. Preserves the M3 testing evidence while keeping historical data clean.
- **Depot code is `DEP-UP-MDB` (Moradabad), not `MBD`.** The earlier PROJECT_STATE.md carried a typo. Simulator loads codes from the DB at runtime; the doc has been corrected.
- **Blend policy as a 10% → 20% ramp, not a locked 20%.** Reflects the actual E20 rollout and gives the baseline forecaster a genuine non-seasonal trend to decompose — a stronger viva story than a flat target with pure seasonality.
- **Direct SQLAlchemy writes are the right choice for historical seeding.** Routing 1,300 orders through the REST API would triple the time budget for zero architectural gain. The live write path remains REST; historical seeding is a distinct concern. Codified as ADR #36.
- **Single `numpy.random.default_rng(seed)` threaded through both generators.** Determinism requires *one* RNG instance; leaking to the global `random` module would break reproducibility silently.

---

*End of PROJECT_STATE.md. Waiting for next instruction.*
