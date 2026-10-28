# Guía para agentes

Reglas para cualquier agente de IA (y cualquier persona) que escriba código en este
repositorio. Si algo de aquí choca con lo que el agente "haría normalmente", gana
esta guía. Si una regla no alcanza para decidir, pregunta antes de inventar una
estructura nueva.

Marketplace de alojamientos temporales de Inversiones LR. Monolito modular:
backend en Spring Boot y frontend en React, en el mismo repositorio. Las decisiones
de arquitectura están en `docs/adr/README.md` y el flujo de Git en `CONTRIBUTING.md`.

## Comandos

```bash
# Backend (Java 21, Maven)
cd backend && mvn verify                 # compila, prueba y genera cobertura JaCoCo
cd backend && mvn spring-boot:run        # http://localhost:8080

# Frontend (Node 20, Vite)
cd frontend && npm ci
cd frontend && npm run lint
cd frontend && npm run test:cobertura    # Vitest + cobertura lcov
cd frontend && npm run dev               # http://localhost:5173
```

## Idioma y nombres

- Todo en español: clases, métodos, variables, rutas, mensajes, comentarios y
  pruebas. Sin tildes ni ñ en identificadores (`Cotizacion`, `resenas`), con
  tildes en textos y comentarios.
- Los sufijos de capa van en inglés porque son el vocabulario de Spring:
  `ReservaController`, `ReservaService`, `ReservaRepository`,
  `ReservaNoEncontradaException`.
- Los módulos son los del catálogo de requerimientos, y se llaman igual en el
  backend, en el frontend y en los commits: `identidad`, `alojamientos`,
  `disponibilidad`, `busqueda`, `cotizacion`, `reservas`, `cancelacion`,
  `notificaciones`, `resenas`, `admin`, `dashboard`, `reportes`, `auditoria`.

## Backend

### Estructura

Un paquete por módulo, y dentro de cada módulo las mismas carpetas. Lo transversal
vive en `config` y en `compartido`.

```
backend/src/main/java/co/edu/unbosque/marketplace/
├── reservas/
│   ├── controllers/     ReservaController
│   │   └── dto/         CrearReservaRequest, ReservaResponse
│   ├── services/        ReservaService
│   ├── repository/      ReservaRepository
│   ├── model/           Reserva, EstadoReserva
│   └── exceptions/      ReservaNoEncontradaException, ReservaSuperpuestaException
├── cotizacion/          (mismas carpetas)
├── ...                  un paquete por módulo del catálogo
├── compartido/          eventos internos, excepción base, manejador global de errores
└── config/              seguridad, CORS, OpenAPI, beans transversales
```

Las pruebas replican el mismo paquete en `backend/src/test/java/...`.

Crea solo las carpetas que el módulo necesita. Un módulo sin persistencia no
lleva `repository/`.

`EstadoController`, en el paquete raíz, es la única excepción: es el punto de
verificación del despliegue.

### Reglas entre capas

| Capa | Puede usar | Nunca |
| --- | --- | --- |
| `controllers` | `services` de su módulo, sus `dto` | `repository`, ni devolver entidades de `model` |
| `services` | `repository` y `model` de su módulo, `services` de otros módulos, `compartido` | clases de `controllers` o de `dto` |
| `repository` | `model` de su módulo | lógica de negocio |
| `model` | nada de otras capas | depender de `controllers` o `services` |

- Un módulo habla con otro solo por su `service` o por un evento interno
  (ADR-2). Nunca toques el `repository` ni las entidades de otro módulo.
- Las reglas de negocio viven en `services` (o en métodos de las entidades de
  `model`), nunca en `controllers`.
- Los controladores reciben y devuelven DTO. Los DTO son `record` y validan su
  entrada con Bean Validation (`@NotNull`, `@Future`, ...).
- Las excepciones de cada módulo extienden la excepción base de `compartido`, y un
  único `@RestControllerAdvice` en `compartido` las traduce a respuestas HTTP. No
  atrapes excepciones en los controladores para armar respuestas a mano.
- Inyección por constructor, con campos `private final`. Nada de `@Autowired`
  sobre campos.

### Pruebas

- JUnit 5 con AssertJ. Cada prueba tiene un `@DisplayName` en español que explica
  la regla y un método en camelCase que la nombra
  (`void rechazaFechasQueSeCruzan()`).
- Toda clase de `services` tiene pruebas unitarias. Los flujos que cruzan capas o
  módulos llevan una prueba de integración con `@SpringBootTest`.
- Toda prueba tiene al menos una aserción.
- El Quality Gate de SonarCloud exige 80 % de cobertura en código nuevo. No
  escribas pruebas vacías para inflarla.

## Frontend

### Estructura

Un módulo por carpeta, con los mismos nombres que el backend. Lo reutilizable vive
en `shared`.

```
frontend/src/
├── app/                      App.jsx, rutas y providers
├── modules/
│   ├── busqueda/
│   │   ├── pages/            BusquedaPage.jsx, BusquedaPage.module.css
│   │   ├── components/       FiltroFechas.jsx, FiltroFechas.module.css, FiltroFechas.test.jsx
│   │   ├── services/         busquedaService.js
│   │   └── models/           alojamiento.js
│   ├── reservas/             (mismas carpetas)
│   └── ...
├── shared/
│   ├── components/           Boton, Modal, Campo: UI sin lógica de negocio
│   ├── services/http.js      cliente HTTP único
│   └── styles/               variables.css (colores, espaciado), global.css
└── main.jsx
```

Hoy solo existen `App.jsx` y `main.jsx` en la raíz de `src`. Al crear la primera
pantalla, mueve `App.jsx` a `app/` y arma la estructura de arriba.

### Reglas

- **El JSX es el HTML.** No hay archivos `.html` por componente: el único es
  `frontend/index.html`.
- Cada componente es una carpeta lógica de tres archivos con el mismo nombre:
  `Componente.jsx`, `Componente.module.css` y `Componente.test.jsx`. Componentes
  y páginas en PascalCase; services y models en camelCase.
- Estilos con CSS Modules (`import estilos from './Componente.module.css'`). Los
  colores y espaciados salen de las variables de `shared/styles/variables.css`;
  no escribas colores sueltos.
- Los componentes nunca llaman a `fetch`. Llaman a una función del `service` de
  su módulo, y todos los services usan `shared/services/http.js`, que centraliza
  la URL base, el token y el manejo de errores.
- `models/` documenta la forma de los datos con JSDoc (`@typedef`) y convierte la
  respuesta de la API al modelo del frontend. El proyecto es JavaScript, no
  TypeScript.
- **Nada de lógica de negocio en el cliente.** Precios, disponibilidad, estados
  de reserva y permisos los decide la API. El frontend muestra lo que la API
  responde y valida solo el formato de los formularios.
- Un módulo no importa nada de otro módulo. Lo que usan dos módulos sube a
  `shared`.

### Pruebas

- Vitest con Testing Library. `describe` con el nombre del componente e `it` en
  español describiendo lo que ve el usuario.
- Busca elementos como los busca el usuario: `getByRole`, `getByLabelText`. Evita
  los selectores por clase o por id de prueba.

## Reglas del dominio que no se negocian

- **Disponibilidad (ADR-3):** PostgreSQL rechaza con una restricción de exclusión
  cualquier reserva que se cruce con otra. Ninguna migración la debilita ni la
  reemplaza por una verificación en código. Confirmar una reserva siempre
  revalida la disponibilidad.
- **Reserva y pago (ADR-4):** la reserva se crea pendiente y el pago va aparte.
  Las operaciones que crean o confirman reservas son idempotentes.
- **Autorización (ADR-5):** cada endpoint declara los roles que pueden usarlo
  (huésped, anfitrión, administrador). Lo que no está declarado se niega.
  Se declara con `@PreAuthorize` sobre el método (o la clase) del controlador,
  usando las constantes de `compartido/seguridad/Permisos`
  (`@PreAuthorize(Permisos.ANFITRION)`). Un endpoint de `/api/v1/**` sin esa
  anotación responde 403 con token y 401 sin él.
- **Trazabilidad (RF-45):** los eventos críticos se registran en el log de
  auditoría, y el log nunca contiene contraseñas, tokens ni datos de pago.
- **Credenciales (RNF-17):** ninguna clave se versiona. Toda configuración
  sensible se lee de variables de entorno, y cada variable nueva se agrega a
  `.env.example` y al README.

## Git

El detalle está en `CONTRIBUTING.md`; lo que un agente no puede olvidar:

- Una rama por historia de Jira, desde `main` actualizada:
  `feature/SOF-<n>-<descripcion>`, `fix/SOF-<n>-...`, `chore/SOF-<n>-...`.
- Commits en Conventional Commits con la clave de Jira:
  `feat(reservas): SOF-65 revalidar disponibilidad al confirmar`. El ámbito es
  el módulo.
- PR con título `SOF-<n> <Descripción>`, la plantilla completa y menos de 400
  líneas cambiadas.
- Sin la clave de Jira no hay rama, commit ni PR. Si no sabes cuál es, pregunta.

## Antes de dar algo por terminado

- [ ] El código está en la carpeta que le corresponde según esta guía.
- [ ] Hay pruebas nuevas, y `mvn verify` o `npm run lint && npm run test:cobertura`
      pasan.
- [ ] No hay credenciales, `console.log`, `System.out.println` ni código
      comentado.
- [ ] Si cambió un endpoint, el contrato OpenAPI lo refleja (se genera con
      springdoc, revisa `/v3/api-docs`).
- [ ] Si cambió una decisión, se actualizó el ADR y Confluence.
