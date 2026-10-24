# Marketplace de Alojamientos · Inversiones LR

Plataforma web que centraliza la oferta de alojamientos temporales de Inversiones LR:
publicación, búsqueda, disponibilidad, cotización, reserva y cancelación, bajo tres
roles —huésped, anfitrión y administrador— con una única fuente de disponibilidad que
impide reservas superpuestas.

Proyecto del curso **Ingeniería de Software II**, Universidad El Bosque, 2026-2.
Docente: Ing. Andrés Felipe Rey.

## Equipo

- Ricardo Alberto Carrero Baltor
- Samuel Steven Calero Sánchez
- Nicolás Zambrano Abril

## Arquitectura

Monolito modular en tres niveles: cada módulo del negocio tiene sus propias capas. Las decisiones
están registradas como ADR en Confluence; el resumen:

| Decisión | Qué se decidió |
| --- | --- |
| ADR-1 | Capas dentro de cada módulo (`controllers`, `services`, `repository`, `model`, `exceptions`); un módulo solo usa a otro por su servicio o por eventos |
| ADR-2 | Los módulos secundarios se comunican por eventos internos guardados junto con el cambio |
| ADR-3 | PostgreSQL rechaza con una restricción de exclusión cualquier reserva que se cruce |
| ADR-4 | La reserva se crea pendiente, el pago va aparte y un proceso concilia lo que quede a medias |
| ADR-5 | Autorización central: cada operación declara sus roles y lo no declarado se niega |

## Estructura

```
marketplace-lr/
├── .github/
│   ├── workflows/ci.yml          build, pruebas y análisis en cada PR
│   ├── pull_request_template.md
│   ├── ISSUE_TEMPLATE/defecto.md
│   └── CODEOWNERS
├── backend/                      Spring Boot · Java 21 · Maven
├── frontend/                     React · Vite · Node 20
├── docs/
│   ├── adr/                      copia local de los ADR
│   └── openapi.yaml              contrato generado desde el código
├── AGENTS.md                     estructura y convenciones de código
├── CLAUDE.md                     carga AGENTS.md en Claude Code
└── CONTRIBUTING.md               flujo Git, ramas y commits
```

## Stack

| Capa | Tecnología |
| --- | --- |
| Interfaz | React 18, Vite, Vitest |
| API | Java 21, Spring Boot 3.3, springdoc-openapi |
| Datos | PostgreSQL 16 |
| Pagos | Stripe en modo prueba |
| Calidad | GitHub Actions, SonarCloud, JaCoCo |

## Cómo levantarlo

Requisitos: Java 21, Maven 3.9, Node 20 y PostgreSQL 16.

```bash
# Backend  ·  http://localhost:8080
cd backend
mvn spring-boot:run

# Frontend ·  http://localhost:5173
cd frontend
npm install
npm run dev
```

El contrato OpenAPI queda publicado en `http://localhost:8080/v3/api-docs` y la
interfaz para explorarlo en `http://localhost:8080/swagger-ui.html`.

## Pruebas

```bash
cd backend  && mvn verify           # unitarias + cobertura JaCoCo
cd frontend && npm run test:cobertura
```

## Variables de entorno

Ninguna credencial se versiona (RNF-17). Copien `.env.example` a `.env` y
complétenlo localmente.

| Variable | Para qué |
| --- | --- |
| `SPRING_DATASOURCE_URL` | Conexión a PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuario de la base de datos |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de la base de datos |
| `STRIPE_CLAVE_PRUEBA` | Clave de la pasarela en modo prueba |

## Documentación

La documentación de ingeniería vive en Confluence: visión y alcance, requerimientos,
casos de uso, procesos BPMN, ASR y escenarios de calidad, tácticas, patrones, ADR,
C4 y despliegue. El backlog y la trazabilidad están en Jira, proyecto **SOF**.

## Cómo contribuir

Lean [CONTRIBUTING.md](CONTRIBUTING.md): toda integración a `main` pasa por pull
request con al menos una aprobación, el pipeline en verde y el Quality Gate aprobado.
