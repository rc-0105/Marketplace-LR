# Tasks: SOF-36 — Host-Side Reservation Listing

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~650–750 (8 prod files, mostly small; 2 new test classes + 1 appended case) |
| 400-line budget risk | Low (relative to the 800-line session budget) |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | auto-chain |
| Chain strategy | pending (not triggered) |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

Backend-only, additive, read-only slice (~6 small prod files + 2 test classes); estimate stays
comfortably under the 800-line budget, so auto-chain should NOT split this into multiple PRs.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Host reservation listing endpoint, end to end | PR 1 (only) | `mvn -Dtest=ReservasAnfitrionServiceTest,ReservasAnfitrionIntegracionTest test` (from `backend/`) | `@SpringBootTest` MockMvc hitting `GET /api/v1/reservas/anfitrion` | Revert the 8 files + 2 new test classes; no migration, no SOF-35 path touched |

## Phase 1: Identidad — Guest Basics Accessor

- [x] 1.1 RED: write `DirectorioUsuariosServiceTest` (mocked `UsuarioRepository`) asserting `consultarBasicos` returns `{}` for empty input and a `Map<Long, HuespedBasico>` otherwise (D4/D5).
- [x] 1.2 GREEN: create `identidad/services/HuespedBasico.java` record `(Long id, String nombre)`.
- [x] 1.3 GREEN: add `UsuarioRepository.findBasicosByIdIn(Collection<Long> ids)` `@Query` projection; create `identidad/services/DirectorioUsuariosService.java` with `@Transactional(readOnly = true) consultarBasicos`.

## Phase 2: Repository — Host Reservation Query

- [x] 2.1 RED: append a case to `ReservaRepositoryIntegracionTest` asserting all states are returned and order is `llegada ASC, id ASC` (D2/D3; Flat Ordering requirement).
- [x] 2.2 GREEN: add `findByPublicacionIdInOrderByLlegadaAscIdAsc(Collection<Long> publicacionIds)` to `ReservaRepository`.

## Phase 3: Service — ReservasAnfitrionService

- [x] 3.1 RED: write `ReservasAnfitrionServiceTest` (style of `HistorialReservasServiceTest`, fixed `Clock`) covering: `publicacionId` branch calling `exigirPropietario` (404-then-403 ordering); no-`publicacionId` branch via `listarDelAnfitrion`; empty-ids short-circuit returns `List.of()` with zero repo calls (D7); `estadoActual(ahora)` derivation; null `alojamiento`/`huesped` enrichment.
- [x] 3.2 GREEN: create `reservas/services/ReservaDeAnfitrion.java` record `(Reserva, EstadoReserva, AlojamientoReservado, HuespedBasico)`.
- [x] 3.3 GREEN: create `reservas/services/ReservasAnfitrionService.java` with `@Transactional(readOnly = true) delAnfitrion(Long anfitrionId, Long publicacionId)` per Data Flow in design.md.

## Phase 4: Controller + DTO — HTTP Wiring

- [x] 4.1 RED: write `ReservasAnfitrionIntegracionTest` (style of `HistorialReservasIntegracionTest`): AC1 host sees all listings across P1/P2, excludes other hosts; AC2 `?publicacionId=` filters to one publication; AC3 nonexistent id → 404 before ownership → 403 for another host's publication; non-`ANFITRION` caller → 403; zero publications → `200 []`; no `email`/`phone` field in guest payload; `GET /anfitrion` is not shadowed by `GET /{id}`.
- [x] 4.2 GREEN: create `reservas/controllers/dto/ReservaAnfitrionResponse.java` record + `desde(ReservaDeAnfitrion)` factory.
- [x] 4.3 GREEN: add `GET /anfitrion` handler to `ReservaController` with `@PreAuthorize(Permisos.ANFITRION)` overriding the class-level `HUESPED`, wiring `ReservasAnfitrionService` and `publicacionId` as `@RequestParam(required = false)`.

## Phase 5: Verification

- [ ] 5.1 Run `mvn test` from `backend/` — **BLOCKED**: neither `mvn` nor a `mvnw` wrapper is available on this environment's PATH (confirmed: `mvn`/`mvnw` not found via `where`/`find` across common install roots, filesystem-wide search for a Maven distribution or `.m2` repo found nothing). Could not execute. Verified instead by manual trace of RED→GREEN compilation dependencies and line-by-line logic tracing of every test against the production code (see apply-progress Work Unit Evidence). Needs a real `mvn test` run on a machine with Maven before merge.
- [ ] 5.2 Confirm `GET /api/v1/reservas/mias` (SOF-35) and `ReservaRepositoryIntegracionTest` pre-existing cases are unaffected — **Not run** (same blocker as 5.1). Manual review: no SOF-35 file (`HistorialReservasService`, `MisReservasResponse`, `ResumenReservaResponse`, the `/mias` or `/{id}` handlers) was modified; `ReservaRepository` only gained one new derived-query method, and all pre-existing methods/queries in `ReservaRepositoryIntegracionTest` are untouched. Needs confirmation via a real test run.
