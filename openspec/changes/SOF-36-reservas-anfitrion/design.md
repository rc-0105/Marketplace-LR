# Design: SOF-36 — Host-side reservation listing

## Technical Approach

Additive, read-only vertical slice in `reservas`, composing three existing seams instead of new
primitives: `PublicacionService.exigirPropietario` (404-then-403), `listarDelAnfitrion` (id
resolution), `consultarReservados` (accommodation enrichment). One new minimal `identidad`
accessor supplies the guest display name. Shape mirrors `HistorialReservasService` /
`ResumenReservaResponse` so the host view reads like the guest view. No migration, no change to
any SOF-35 path.

## Architecture Decisions

| # | Decision | Alternatives rejected | Rationale |
|---|---|---|---|
| D1 | Dedicated `ReservasAnfitrionService` | Add `delAnfitrion()` to `HistorialReservasService` | That class documents a "del huésped" contract; a host method would need a second Clock-derived rule and two unrelated enrichment paths in one unit. |
| D2 | Repository derived name `findByPublicacionIdInOrderByLlegadaAscIdAsc`, no `@Query` | `@Query` JPQL with `OCUPA`; `@Query` with `ORDER BY CASE` | All states are in scope, so no custom predicate exists — `OCUPA` must **not** be reused. Derived name mirrors the SOF-35 sibling `findByHuespedIdOrderByLlegadaAscIdAsc`. |
| D3 | Order is one monotone `llegada ASC, id ASC`; past arrivals **interleave** (they sort first), they do not move to the end | Upcoming-first then past-DESC (two-group sort); `llegada DESC` from the proposal | Spec scenario fixes `D1, D2, D3` ascending. A monotone key keeps the order independent of request time (no midnight-Bogotá reshuffle, no Clock in a sort), is pushed to Postgres, and stays correct if pagination is added. Supersedes the proposal's `llegada DESC, id DESC`. |
| D4 | New thin `DirectorioUsuariosService` in `identidad` | New method on `CuentaService`; `UsuarioRepository` injected directly into `reservas` | `CuentaService` also restricts, deletes and rotates passwords — `reservas` must not hold that bean. A repository crossing modules breaks ADR-2. |
| D5 | `HuespedBasico(Long id, String nombre)` projection | Return `Usuario`; return `Map<Long, String>` | Mirrors `AlojamientoReservado` exactly (consumer-named record, ids only, no entity across modules) and leaves room for one more field without a signature change. |
| D6 | DTO exposes `String estado` from `estadoActual(ahora).name()` | `EstadoReserva estado` as in `ResumenReservaResponse` | Wire JSON is identical, but a `String` field keeps `COMPLETADA` (SOF-78) out of the contract's closed enum. |
| D7 | Empty publication-id set short-circuits in the service | Let Hibernate render an empty `IN` | Guarantees `200 []` for a host with zero publications with zero DB round-trips. |
| D8 | Guest `id` is not exposed; only `nombre` | `huespedId` + `huespedNombre` | Minimal privacy surface; no host operation in this slice is keyed by guest id. |

## Data Flow

    GET /api/v1/reservas/anfitrion[?publicacionId]
      │  @PreAuthorize(ANFITRION) overrides the class-level HUESPED
      ▼
    ReservaController ── anfitrionId = Long.valueOf(jwt.getSubject())
      ▼
    ReservasAnfitrionService.delAnfitrion(anfitrionId, publicacionId)
      ├─ publicacionId != null → publicaciones.exigirPropietario(id, anfitrionId)  → 404 / 403
      │                          ids = Set.of(publicacionId)
      ├─ publicacionId == null → ids = listarDelAnfitrion(anfitrionId) → map Publicacion::getId
      ├─ ids.isEmpty() → return List.of()                                   (D7)
      ├─ reservas.findByPublicacionIdInOrderByLlegadaAscIdAsc(ids)          (D2/D3)
      ├─ publicaciones.consultarReservados(publicacionIds)  → Map<Long, AlojamientoReservado>
      ├─ usuarios.consultarBasicos(huespedIds)              → Map<Long, HuespedBasico>  (D4)
      └─ per row: new ReservaDeAnfitrion(reserva, reserva.estadoActual(ahora), aloj, huesped)
      ▼
    List<ReservaAnfitrionResponse>  (ReservaAnfitrionResponse::desde)

Three reads run inside one `@Transactional(readOnly = true)` method so the list is a single
consistent snapshot. `ahora = Instant.now(reloj)` is read once per request.

## File Changes

| File | Action | Description |
|---|---|---|
| `reservas/repository/ReservaRepository.java` | Modify | `+ List<Reserva> findByPublicacionIdInOrderByLlegadaAscIdAsc(Collection<Long> publicacionIds)` |
| `reservas/services/ReservasAnfitrionService.java` | Create | Orchestration + enrichment (D1) |
| `reservas/services/ReservaDeAnfitrion.java` | Create | Service record, sibling of `ReservaConsultada` |
| `reservas/controllers/dto/ReservaAnfitrionResponse.java` | Create | Row DTO + `desde(...)` |
| `reservas/controllers/ReservaController.java` | Modify | `GET /anfitrion` handler, `ANFITRION` override, new dependency |
| `identidad/services/DirectorioUsuariosService.java` | Create | Batch name accessor (D4) |
| `identidad/services/HuespedBasico.java` | Create | Projection record (D5) |
| `identidad/repository/UsuarioRepository.java` | Modify | `+ findBasicosByIdIn` projection query |

## Interfaces / Contracts

```java
// reservas/repository/ReservaRepository.java — all states, nearest arrival first (SOF-36).
List<Reserva> findByPublicacionIdInOrderByLlegadaAscIdAsc(Collection<Long> publicacionIds);

// identidad/services/HuespedBasico.java
public record HuespedBasico(Long id, String nombre) { }

// identidad/services/DirectorioUsuariosService.java
@Transactional(readOnly = true)
public Map<Long, HuespedBasico> consultarBasicos(Collection<Long> usuarioIds) // {} if empty

// identidad/repository/UsuarioRepository.java
@Query("SELECT new co.edu.unbosque.marketplace.identidad.services.HuespedBasico(u.id, u.nombre)"
        + " FROM Usuario u WHERE u.id IN :ids")
List<HuespedBasico> findBasicosByIdIn(@Param("ids") Collection<Long> ids);

// reservas/services/ReservaDeAnfitrion.java
public record ReservaDeAnfitrion(Reserva reserva, EstadoReserva estadoActual,
        AlojamientoReservado alojamiento, HuespedBasico huesped) { }

// reservas/controllers/dto/ReservaAnfitrionResponse.java
public record ReservaAnfitrionResponse(Long id, Long publicacionId, String titulo, String ciudad,
        String imagenPrincipal, String huespedNombre, LocalDate llegada, LocalDate salida,
        long noches, String estado, BigDecimal total) { }

// reservas/services/ReservasAnfitrionService.java
@Transactional(readOnly = true)
public List<ReservaDeAnfitrion> delAnfitrion(Long anfitrionId, Long publicacionId);

// reservas/controllers/ReservaController.java
@GetMapping("/anfitrion")
@PreAuthorize(Permisos.ANFITRION)
public List<ReservaAnfitrionResponse> delAnfitrion(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long publicacionId) { ... }
```

`titulo` / `ciudad` / `imagenPrincipal` are `null` when the publication is gone, and
`huespedNombre` is `null` when the account row is gone — the same convention as
`ResumenReservaResponse`. A deleted account is anonymized, not removed, so it surfaces as
`Usuario.NOMBRE_ELIMINADO`.

## Error Handling

No new `@ExceptionHandler`. `PublicacionService.exigirPropietario` already raises
`PublicacionNoEncontradaException` (404) before `Propiedad.exigirPropietario` raises
`AccesoDenegadoException` (403); both extend `NegocioException` and are translated by the single
existing `ManejadorErrores.negocio(...)` into RFC 9457 `ProblemDetail`. A non-host caller is
rejected by `@PreAuthorize` before the controller body. No administrator branch exists.

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Unit | `ReservasAnfitrionServiceTest`: both id-resolution branches, empty-ids short-circuit (D7), `estadoActual` derivation, null alojamiento/huésped | JUnit + Mockito, fixed `Clock` |
| Integration | `ReservasAnfitrionIntegracionTest`: AC1 all listings, AC2 `?publicacionId=`, AC3 404 then 403, non-host 403, `200 []`, no email/phone field, `/anfitrion` is not shadowed by `GET /{id}` | `@SpringBootTest` + MockMvc, mirroring `HistorialReservasIntegracionTest` |
| Integration | Append one case to `ReservaRepositoryIntegracionTest` asserting all states returned and `llegada ASC, id ASC` | Existing Postgres-backed harness |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The two web-layer concerns are covered above: path precedence of
the literal `/anfitrion` over `GET /{id}` (asserted by test) and authorization (D-level
`@PreAuthorize` + `exigirPropietario`).

## Migration / Rollout

No migration required. Purely additive and read-only. Frontend ships as a chained PR.

## Open Questions

- [ ] None blocking. Accepted consequence of D3: a host with long history sees past arrivals at
  the top; a `desde=` filter or client-side scroll would address it in a later slice.
