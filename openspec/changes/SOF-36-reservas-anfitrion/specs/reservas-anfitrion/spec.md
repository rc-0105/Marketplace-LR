# Reservas Anfitrión Specification

## Purpose

Lets an authenticated host (ANFITRION) list reservations held across their own publicaciones,
optionally filtered to one publicación, so they can plan check-ins, cleaning, and staffing.
Closes the gap where reservation data was only reachable from the guest side
(`GET /api/v1/reservas/mias`).

## Requirements

### Requirement: Host Reservation Listing Endpoint

The system MUST expose `GET /api/v1/reservas/anfitrion`. With no `publicacionId` parameter it
MUST return every reservation belonging to a publicación owned by the authenticated host,
across all reservation states. Each item MUST include the guest's display name, check-in date,
check-out date, publicación id, and current state.

The contract MUST NOT enumerate reservation states against a closed set. State MUST be derived
via `Reserva.estadoActual(ahora)` at request time, so a future state (e.g. `COMPLETADA`) surfaces
without a contract change.

#### Scenario: Host lists all reservations across their listings

- GIVEN a host owns publicaciones P1 and P2, each with reservations in different states
- WHEN the host calls `GET /api/v1/reservas/anfitrion`
- THEN the response is `200 OK` and contains every reservation of P1 and P2 with guest name,
  check-in, check-out, publicación id, and current state
- AND it contains no reservation of another host's publicación

#### Scenario: Host with no publications sees an empty list

- GIVEN a host owns zero publicaciones
- WHEN the host calls `GET /api/v1/reservas/anfitrion`
- THEN the response is `200 OK` with an empty list, not `404`

#### Scenario: All states are visible, including future states

- GIVEN a host's publicación has reservations currently PENDIENTE, CONFIRMADA, CANCELADA, and
  VENCIDA
- WHEN the host calls `GET /api/v1/reservas/anfitrion`
- THEN every reservation appears regardless of state
- AND a reservation whose `estadoActual(ahora)` later resolves to a new state (e.g.
  `COMPLETADA`) is returned without any contract change

### Requirement: Optional Filtering by Publicación

When `publicacionId` is supplied, the system MUST return only that publicación's reservations
across all states, validating in order: existence, then ownership, then the query — mirroring
`PublicacionService.exigirPropietario` and `HistorialReservasService.detalle()`.

#### Scenario: Host filters by an owned publication

- GIVEN a host owns publicación P1 (with reservations) and also owns P2
- WHEN the host calls `GET /api/v1/reservas/anfitrion?publicacionId=P1`
- THEN the response is `200 OK` and contains only P1's reservations

#### Scenario: Nonexistent publicacionId returns 404 before any ownership check

- GIVEN a host and a `publicacionId` that does not exist
- WHEN the host calls `GET /api/v1/reservas/anfitrion?publicacionId={id}`
- THEN the response is `404 Not Found`, never `403`, for a nonexistent id

#### Scenario: Publicación owned by another host returns 403

- GIVEN host A and a publicación owned by host B
- WHEN host A calls `GET /api/v1/reservas/anfitrion?publicacionId={B's id}`
- THEN the response is `403 Forbidden`

### Requirement: Host-Only Access

The system MUST restrict this endpoint to callers holding the ANFITRION permission via
method-level `@PreAuthorize(Permisos.ANFITRION)`. No administrator bypass MUST exist.

#### Scenario: Non-host caller is rejected

- GIVEN an authenticated user without the ANFITRION permission
- WHEN the user calls `GET /api/v1/reservas/anfitrion`
- THEN the response is `403 Forbidden`

### Requirement: Guest Data Minimization

The response MUST include only the guest's display name. It MUST NOT include guest email,
phone, or any other guest attribute.

#### Scenario: Guest contact details are absent

- GIVEN a reservation exists on the host's publicación
- WHEN the host retrieves the reservation list
- THEN the guest is represented only by a display name
- AND no email or phone field is present

### Requirement: Flat Ordering, No Pagination

The system MUST return a single flat list ordered by check-in date ascending (closest upcoming
arrival first), with no future/past split, and MUST NOT paginate in this delivery.

#### Scenario: Reservations are ordered by nearest arrival first

- GIVEN a host has reservations with check-in dates D1 < D2 < D3
- WHEN the host calls `GET /api/v1/reservas/anfitrion`
- THEN the reservations appear in order D1, D2, D3, not split into future/past groups
