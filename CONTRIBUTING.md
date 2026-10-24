# Cómo contribuir

Toda integración a `main` pasa por pull request. La clave de Jira viaja en la rama,
en los commits y en el PR, de modo que Jira enlaza el trabajo solo y la matriz de
trazabilidad que exige el enunciado se arma sin esfuerzo extra.

La clave del proyecto en Jira es **SOF**.

La estructura de carpetas y las convenciones de código están en
[AGENTS.md](AGENTS.md). Vale igual si escribes a mano o con un agente de IA.

## El flujo, de principio a fin

1. Toma una historia del tablero y pásala a *En curso*.
2. Crea la rama desde `main` actualizada.
3. Trabaja en commits pequeños que mencionen la historia.
4. Abre el pull request. El pipeline corre solo.
5. Resuelve lo que señale tu revisor.
6. Con el pipeline en verde y una aprobación, haz *squash merge*.
7. La rama se borra sola al integrar.

## Ramas

Una rama por historia, creada desde `main` actualizada y eliminada tras el merge.
No usamos `develop`: con PR obligatorios y `main` protegida, un solo tronco alcanza
para un MVP.

| Tipo | Patrón | Ejemplo |
| --- | --- | --- |
| Funcionalidad | `feature/SOF-<n>-<descripcion-corta>` | `feature/SOF-65-revalidar-disponibilidad` |
| Corrección | `fix/SOF-<n>-<descripcion>` | `fix/SOF-31-total-cotizacion` |
| Documentación o CI | `chore/SOF-<n>-<descripcion>` | `chore/SOF-1-configuracion-repositorio` |
| Urgente sobre un release | `hotfix/SOF-<n>-<descripcion>` | `hotfix/SOF-2-login-caido` |

## Commits

Conventional Commits con la clave al inicio del asunto:

```
<tipo>(<módulo>): SOF-<n> <descripción en imperativo>

feat(reservas): SOF-65 revalidar disponibilidad al confirmar
test(reservas): SOF-77 prueba de concurrencia con 50 solicitudes
fix(cotizacion): SOF-31 sumar servicios adicionales al total
docs(adr): SOF-1 cerrar ADR-3 con la restricción de exclusión
```

**Tipos:** `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `ci`.

**Módulos**, los mismos del catálogo de requerimientos: `identidad`, `alojamientos`,
`disponibilidad`, `busqueda`, `cotizacion`, `reservas`, `cancelacion`,
`notificaciones`, `resenas`, `admin`, `dashboard`, `reportes`, `auditoria`.

## Pull requests

- **Título:** `SOF-65 Revalidar disponibilidad al confirmar la reserva`.
- **Tamaño:** por debajo de 400 líneas. Si crece más, divide la historia.
- **Merge:** *squash merge*, para que cada historia quede como un solo commit en
  `main` con el número de PR.
- **Revisores:** mínimo uno distinto del autor. El autor responde cada comentario
  de sus compañeros antes de pedir el merge.

## Definición de hecho

- [ ] Criterios de aceptación de la historia verificados.
- [ ] Pruebas unitarias y, si aplica, de integración agregadas y en verde.
- [ ] Quality Gate de SonarCloud aprobado.
- [ ] Una aprobación humana.
- [ ] Contrato OpenAPI y documentación de Confluence actualizados si cambió un
      contrato o una decisión.
- [ ] Matriz de trazabilidad actualizada con el PR y la prueba.

## Qué revisa el pipeline

En cada pull request contra `main` corren dos trabajos, y ambos deben pasar:

| Trabajo | Qué hace |
| --- | --- |
| `Backend · build, pruebas y Sonar` | Compila con Maven y Java 21, corre las pruebas contra un PostgreSQL 16 de servicio, mide cobertura con JaCoCo y analiza en SonarCloud |
| `Frontend · build, pruebas y Sonar` | Instala con `npm ci`, revisa estilo con ESLint, corre Vitest con cobertura lcov, compila y analiza en SonarCloud |

## Reglas que protegen `main`

No se puede empujar directo a `main`, ni forzar, ni borrarla. El merge solo se
habilita con el pipeline en verde, una aprobación y todas las conversaciones
resueltas. La lista de excepciones está vacía a propósito: ni el administrador se
salta las reglas, y eso es justamente la evidencia de rama protegida que pide el
enunciado.
