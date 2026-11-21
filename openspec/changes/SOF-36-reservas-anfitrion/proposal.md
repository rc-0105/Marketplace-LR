# Proposal: SOF-36 — Host-side reservation listing (HU-25, RF-39)

## Intent

Hosts can publish accommodations and manage availability but cannot see who is booked into
them — reservation data is reachable only from the guest side (`GET /api/v1/reservas/mias`).
Hosts therefore cannot plan check-ins, cleaning, or staffing. Success: a host retrieves the
reservations of their own accommodations (guest, dates, state) and never sees anyone else's.

## Scope

### In Scope
- `GET /api/v1/reservas/anfitrion?publicacionId=` — optional filter; omitted returns all
  reservations across the caller's publications. Method-level `@PreAuthorize(Permisos.ANFITRION)`.
- Dedicated `ReservasAnfitrionService`.
- New `ReservaRepository` query: all-state reservations by `publicacionId` collection.
- Minimal cross-module accessor in `identidad` for batch guest display name.
- New response DTO carrying guest identity (absent from all SOF-35 DTOs).
- Unit + integration tests named per AC1/AC2/AC3.

### Out of Scope
- **Frontend** (`/anfitrion/reservas` page, route, service fn, mapper). ACs describe only the
  API contract; the host UI is net-new with no component to extend, and bundling it breaks the
  400-line review budget. Deferred to a chained follow-up PR.
- Guest email (name only), pagination, date/state filters, futuras/pasadas split, changes to
  any SOF-35 path.
- No DB migration — read-only over existing `reserva` / `publicacion`.

## Capabilities

### New Capabilities
- `reservas-anfitrion`: a host lists reservations of their own accommodations, optionally
  filtered to one publication, with ownership enforcement.

### Modified Capabilities
- None.

## Approach

Reuse over rebuild. `PublicacionService.listarDelAnfitrion` resolves the host's publication ids
(unfiltered branch). `PublicacionService.exigirPropietario` already implements AC3's exact
404-then-403 sequence, so the filtered branch calls it first — no new security primitive.
`consultarReservados` enriches with accommodation data, as `HistorialReservasService` does.
Validation order mirrors `HistorialReservasService.detalle()`: publication missing → 404, not
mine → 403, then query.

Decisions: (1) dedicated service, preserving `HistorialReservasService`'s "del huésped"
contract; (2) accessor shape `HuespedBasico(Long id, String nombre)` +
`consultarBasicos(Collection<Long>)` → `Map`, mirroring `AlojamientoReservado` and respecting
ADR-2 (ids only across modules); (3) flat list ordered `llegada DESC, id DESC`; (4) host with
zero publications → `200` + empty list, never `404`; (5) state rendered via
`Reserva.estadoActual(ahora)`, never a hardcoded enum list.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `reservas/controllers/ReservaController.java` | Modified | `GET /anfitrion` handler, `ANFITRION` override |
| `reservas/services/ReservasAnfitrionService.java` | New | Orchestration + enrichment |
| `reservas/repository/ReservaRepository.java` | Modified | All-state query by publication ids |
| `reservas/controllers/dto/` | New | Host response DTO with guest identity |
| `identidad/services/` | New | `HuespedBasico` record + batch accessor |
| `reservas/.../*Test.java` | New | Unit + integration, AC-named |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| New identidad seam over-exposes `Usuario` | Med | Name-only projection; no entity crosses modules |
| SOF-78 adds `COMPLETADA` after this ships | High | Derive state from `estadoActual(ahora)` |
| Unbounded result set for large hosts | Low | Accepted; pagination is a named non-goal |
| Ordering not what hosts expect | Med | One-line repository change; confirm in spec phase |

## Rollback Plan

Purely additive and read-only. Revert the branch: delete the new service, DTO, identidad
accessor, and tests; drop the `GET /anfitrion` handler and the added repository method. No
migration to undo, no existing endpoint altered, no guest-side behavior affected.

## Dependencies

- SOF-33 (create reservation) — merged.
- SOF-78 (`COMPLETADA`) — unmerged sibling branch, not blocking. The stale
  `CierreReservasService.class` in `backend/target/classes` with no matching `.java` is build
  residue from that branch, not a SOF-36 defect.

## Success Criteria

- [ ] AC1: host sees their accommodations' reservations with guest, dates, state.
- [ ] AC2: `?publicacionId=` returns only that publication's reservations.
- [ ] AC3: another host's `publicacionId` → `403`; nonexistent → `404`.
- [ ] No SOF-35 endpoint, DTO, or test modified; no new migration.
- [ ] Backend PR within the 400-line review budget.
