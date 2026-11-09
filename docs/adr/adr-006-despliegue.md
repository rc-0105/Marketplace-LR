# ADR-6: Despliegue continuo en Render con una sola imagen

**Estado:** Propuesta · **Implementada en:** SOF-97

## Contexto

Solo había integración continua: ninguna imagen, ningún entorno ni secretos de
hosting. El frontend llama a la API con rutas relativas (`/api/...`) y el backend no
configura CORS. Flyway migra al arrancar, y la restricción de exclusión de ADR-3
necesita la extensión `btree_gist`.

## Problema o necesidad

Que cada integración a `main` con el CI en verde quede desplegada y verificada sin
pasos manuales, y que se pueda saber de qué commit salió cada versión.

## Alternativas consideradas

Render, Railway, Fly.io y Azure; frontend y backend juntos o por separado.

## Trade-offs identificados

Railway y Fly.io piden crédito o tarjeta; Azure exige más configuración para un MVP.
Separar frontend y backend obliga a agregar CORS o rewrites y a coordinar dos
despliegues. El plan gratuito de Render duerme el servicio tras 15 minutos sin uso
(la primera petición tarda) y la base gratuita vence a los 30 días.

## Decisión seleccionada

- Una imagen Docker (`Dockerfile`): Spring Boot sirve la API y el build de React
  desde el mismo origen, sin CORS. Las rutas del SPA devuelven `index.html`
  (`config/RutasFrontend`); `/api/**` y el actuator siguen protegidos igual.
- Render con PostgreSQL 16 en la misma región, descritos en `render.yaml`.
- Render no despliega por su cuenta (`autoDeploy: false`). El workflow `Despliegue`
  corre cuando termina el CI de un push a `main`, solo si pasó: pide el despliegue de
  ese commit con el deploy hook y espera a que `/api/estado` responda `activa` con
  ese commit en `version`. Si no responde en 20 minutos, la corrida falla.
- Las credenciales viven en Render y en los secretos de GitHub, nunca en el
  repositorio (RNF-17).

## Consecuencias

- Revertir es volver a desplegar un commit anterior. Por eso una migración nunca
  debe romper la versión anterior: primero se agregan columnas o tablas, y lo que se
  quita se quita en un despliegue posterior.
- Si se cambia de proveedor, solo cambian `render.yaml` y el paso que llama al
  deploy hook; la imagen y la verificación se conservan.
