# Product

## Register

product

## Users

Tres roles comparten la plataforma. Cuando hay que priorizar, gana el huésped.

- **Huésped (prioridad).** Persona que busca un alojamiento temporal en una ciudad
  y unas fechas concretas. Llega con una necesidad clara (dónde, cuándo, cuántos)
  y quiere saber rápido qué hay disponible, cuánto cuesta en total y si puede
  confiar en lo que va a pagar. Su recorrido: buscar por ciudad y fechas, filtrar
  por capacidad, tipo, precio y servicios, comparar, ver el detalle, cotizar,
  reservar, pagar y, al terminar la estancia, reseñar. También consulta y cancela
  sus reservas.
- **Anfitrión.** Publica y administra sus alojamientos: crea la publicación,
  carga imágenes, define precio, limpieza, servicios adicionales, política de
  cancelación y modo de reserva, registra periodos disponibles, aprueba
  solicitudes y revisa las reservas de sus propiedades. Vuelve seguido y necesita
  ver el estado de todo de un vistazo.
- **Administrador.** Equipo de Inversiones LR. Aprueba o rechaza publicaciones,
  modera reseñas, publicaciones y usuarios (siempre con motivo), configura las
  tarifas de servicio y descuento, consulta el dashboard, exporta reportes y
  revisa la auditoría. Trabaja con volumen y necesita precisión, no adornos.

## Product Purpose

Marketplace de alojamientos temporales de Inversiones LR: centraliza la oferta en
un solo lugar con una única fuente de disponibilidad, para que nunca se crucen dos
reservas y lo que el huésped ve disponible esté disponible de verdad.

El éxito se mide en que un huésped pase de "busco en Cartagena del 12 al 15" a una
reserva confirmada y pagada sin dudas en el camino: el precio total se conoce antes
de pagar, sin sorpresas, y cada estado de la reserva (pendiente, confirmada,
cancelada, completada) se entiende sin explicación. Para el anfitrión, que publicar
y mantener la disponibilidad al día sea rápido. Para el administrador, que moderar
deje rastro y no admita errores silenciosos.

## Brand Personality

**Confiable, clara, cálida.**

- **Confiable:** el usuario va a pagar y a dormir en un lugar que no conoce. La
  interfaz transmite seguridad con hechos (precio desglosado, política de
  cancelación visible, calificaciones reales, estados explícitos), no con sellos
  ni promesas.
- **Clara:** se entiende sin pensar. Una acción principal por pantalla, lenguaje
  simple, números exactos.
- **Cálida:** cercana, no corporativa. Habla de tú, en español neutro y amable, y
  deja que la fotografía de los alojamientos aporte la emoción.

Referencia de sensación: **Airbnb**. Lo que se toma de ahí es la fotografía como
protagonista, el aire generoso, el flujo de reserva guiado paso a paso y el precio
siempre a la vista. Lo que no se toma es su identidad visual: este producto tiene
la suya.

## Anti-references

- **Plantilla genérica de IA:** degradados violetas, grillas de tarjetas idénticas,
  íconos dentro de círculos, todo centrado, métricas gigantes con etiquetas
  diminutas.
- **Portal inmobiliario viejo:** pantallas saturadas de banners, filtros eternos,
  letra chica, azul corporativo por todos lados.
- **Booking agresivo:** urgencia falsa ("quedan 2", contadores, "otras 14 personas
  están mirando"), rojo como presión, patrones oscuros para apurar la compra.
- **Panel de admin gris:** tablas sin jerarquía y formularios interminables sin
  guía, aunque sean pantallas internas.

## Design Principles

1. **El precio nunca sorprende.** Desde la búsqueda hasta el pago, el usuario sabe
   cuánto va a pagar. El total se muestra desglosado (noches, limpieza, servicio,
   adicionales, descuentos) antes de cualquier compromiso, tal como lo calcula la
   API.
2. **Confianza con evidencia, no con presión.** Se gana mostrando información
   verificable: fotos reales, política de cancelación, calificación y reseñas, el
   estado exacto de cada reserva. Nunca urgencia fabricada.
3. **Una tarea por pantalla.** Cada vista tiene una acción principal evidente. Lo
   secundario se revela cuando hace falta, no compite con lo principal.
4. **El estado siempre a la vista.** Reservas, publicaciones y cuentas cambian de
   estado; el usuario siempre sabe en cuál está cada cosa, qué significa y qué
   puede hacer después.
5. **Herramienta seria para quien trabaja, vitrina para quien viaja.** Las zonas de
   anfitrión y administrador priorizan densidad, precisión y rapidez; la zona de
   huésped prioriza fotografía y calma. Mismo sistema, distinto ritmo.

## Accessibility & Inclusion

- **WCAG 2.2 nivel AA** como mínimo en todas las pantallas, incluidas las de
  administración.
- Contraste de 4.5:1 para texto normal y 3:1 para texto grande y componentes de
  interfaz.
- Todo se puede usar con teclado, con foco siempre visible y orden lógico.
- Ninguna información depende solo del color: los estados de reserva y del
  calendario de disponibilidad llevan texto o ícono además del color.
- Se respeta `prefers-reduced-motion`.
- Formularios con etiquetas visibles y errores que dicen qué pasó y cómo
  corregirlo, en el lugar del error.
- Objetivos táctiles de al menos 24×24 px; la búsqueda y la reserva funcionan
  completas en el celular.
