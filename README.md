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
| `JWT_SECRET` | Firma de los tokens de sesión. Obligatorio, mínimo 32 bytes; sin él la API no arranca. Genéralo con `openssl rand -base64 48` |
| `JWT_EXPIRACION_MINUTOS` | Vigencia del token en minutos (por defecto 60) |
| `CORREO_MODO` | `log` (por defecto) escribe el código de recuperación en la consola; `smtp` lo envía por correo |
| `MAIL_HOST`, `MAIL_PORT` | Servidor SMTP. Por defecto `smtp.gmail.com` y `587` (STARTTLS) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Cuenta SMTP. Con Gmail, la contraseña es una [contraseña de aplicación](https://myaccount.google.com/apppasswords), no la de la cuenta |
| `MAIL_FROM` | Remitente de los correos. Si se omite, se usa `MAIL_USERNAME` |
| `STRIPE_CLAVE_PRUEBA` | Clave de la pasarela en modo prueba |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Solo en la imagen Docker: si falta `SPRING_DATASOURCE_URL`, se arma con estos tres (así los entrega Render) |
| `VERSION_COMMIT` | Commit que informa `/api/estado`. En Render se usa `RENDER_GIT_COMMIT`; sin ninguno, `desarrollo` |

## Despliegue

Producción corre en Render con una sola imagen (`Dockerfile`): Spring Boot sirve la
API y el frontend desde el mismo origen. La decisión está en
[ADR-6](docs/adr/adr-006-despliegue.md).

Cada push a `main` con el CI en verde se despliega solo: el workflow `Despliegue`
pide a Render ese commit y espera a que `/api/estado` responda con él en `version`.
Un PR o un CI fallido no despliegan nada.

### Puesta en marcha (una sola vez)

1. En Render: **New → Blueprint**, elige este repositorio y aplica `render.yaml`.
   Crea el servicio `marketplace-lr` y la base `marketplace-db`, y genera
   `JWT_SECRET`. Las migraciones de Flyway, `btree_gist` incluida, corren al arrancar.
2. En el servicio de Render, **Settings → Deploy Hook**: copia la URL.
3. En GitHub, **Settings → Environments → New environment** `produccion`:
   - secreto `RENDER_DEPLOY_HOOK_URL` con la URL del paso 2;
   - variable `URL_PRODUCCION` con la URL pública del servicio, sin `/` final
     (por ejemplo `https://marketplace-lr.onrender.com`).
4. En GitHub, **Settings → Rules → Rulesets**: activa "Proteccion de main" para que
   un push directo a `main` se rechace.

### Volver a la versión anterior

1. En Render, en **Events** del servicio, elige el último despliegue sano y pulsa
   **Rollback**; o vuelve a llamar el deploy hook con `&ref=<commit anterior>`.
2. Comprueba que `/api/estado` informa ese commit en `version`.
3. Corrige el problema con un PR normal; al integrarlo se despliega solo.

La base no se revierte: por eso las migraciones deben ser compatibles con la versión
anterior (ver ADR-6).

### Probar la imagen en local

```bash
docker build -t marketplace-lr .
docker run --rm -p 8080:8080 --env-file .env marketplace-lr
```

## Documentación

La documentación de ingeniería vive en Confluence: visión y alcance, requerimientos,
casos de uso, procesos BPMN, ASR y escenarios de calidad, tácticas, patrones, ADR,
C4 y despliegue. El backlog y la trazabilidad están en Jira, proyecto **SOF**.

## Cómo contribuir

Lean [CONTRIBUTING.md](CONTRIBUTING.md): toda integración a `main` pasa por pull
request con al menos una aprobación, el pipeline en verde y el Quality Gate aprobado.
