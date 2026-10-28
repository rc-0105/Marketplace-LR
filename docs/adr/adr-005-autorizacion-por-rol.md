# ADR-5: Autorización por rol

**Estado:** Aceptada · **Implementada en:** SOF-13 · **ASR relacionado:** ASR-5 (control de acceso por rol)

## Contexto

Hay tres roles (huésped, anfitrión, administrador) y cada operación debe ejecutarse
solo si el usuario tiene permiso.

## Problema o necesidad

En qué parte del sistema se revisan los permisos, de forma que ninguna operación
quede sin revisar.

## Alternativas consideradas

Revisar en cada operación, una revisión central con permisos declarados, y un
servicio externo de permisos.

## Trade-offs identificados

Revisar en cada operación depende de que nadie lo olvide, y no hay forma
sistemática de comprobarlo. Un servicio externo agrega infraestructura que tres
roles no justifican.

## Decisión seleccionada

Una revisión central, donde cada operación declara qué roles puede usarla y lo que
no se declara se niega.

## Justificación

Se puede probar de forma sistemática, con una sola prueba que recorra todas las
rutas, sin necesitar otro servicio.

## Consecuencias

Se gana seguridad verificable; se pierde algo de flexibilidad, porque cambiar un
rol exige modificar el código y volver a desplegar.

## Implementación

- Cada endpoint de `/api/v1` declara sus roles con `@PreAuthorize`, usando las
  constantes de `compartido/seguridad/Permisos`.
- La revisión central es `config/AutorizacionDeclarada`, un filtro sobre
  `/api/v1/**`. Evalúa la expresión del endpoint antes de leer la petición, y niega
  lo que no declara roles: 403 con token, 401 sin él. Por eso la expresión solo
  puede hablar de roles.
- La propiedad de un recurso (por ejemplo, que un anfitrión solo edite sus
  publicaciones) se verifica en el service con
  `Propiedad.exigirPropietario(propietarioId, usuarioId)`, que responde 403.
- Pruebas: `AutorizacionPorRolIntegracionTest`, `AutorizacionDeclaradaTest` y
  `PropiedadTest`.
