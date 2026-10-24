<!-- SEED: re-run /impeccable document once there's code to capture the actual tokens and components. -->
---
name: Marketplace de Alojamientos · Inversiones LR
description: Encontrar, cotizar y reservar un alojamiento temporal con el precio claro desde el primer momento.
---

# Design System: Marketplace de Alojamientos · Inversiones LR

## 1. Overview

**Creative North Star: "La recepción cálida"**

La interfaz se comporta como la recepción de un buen hospedaje: te recibe con calma,
te dice exactamente qué hay, cuánto cuesta y qué incluye, y te entrega la llave sin
letra chica. Nada grita, nada apura. La emoción la ponen las fotografías de los
alojamientos; la interfaz pone el orden, la claridad y la confianza para pagar.

Es un sistema de producto: familiar donde la familiaridad ayuda (búsqueda, filtros,
calendario, formularios de pago) y propio en los detalles (la calidez de la arcilla,
el aire generoso, el desglose del precio como pieza central). La zona del huésped
respira, con fotografía grande y una acción por pantalla. Las zonas de anfitrión y
administrador usan el mismo sistema con más densidad: tablas legibles, estados
explícitos y formularios guiados.

Tema claro. La escena que lo decide: una persona planea un viaje desde el celular,
de noche en el sofá o en un descanso del trabajo, comparando fotos y precios con
calma antes de pagar. Superficies claras y cálidas que dejan brillar la fotografía.

Lo que este sistema rechaza, con las palabras de PRODUCT.md: la **plantilla genérica
de IA**, el **portal inmobiliario viejo**, el **Booking agresivo** con urgencia
falsa y el **panel de admin gris**.

**Key Characteristics:**
- Neutros cálidos con un único acento terracota, usado con escasez.
- Una sola familia sans humanista para todo, con jerarquía por tamaño y peso.
- Fotografía como protagonista en la zona del huésped.
- El precio total y el estado de cada cosa siempre visibles.
- Movimiento responsivo: cada acción responde, nada se coreografía.

## 2. Colors: La paleta de arcilla

Neutros cálidos teñidos hacia la arcilla y un solo acento terracota que marca qué
se puede hacer y qué está seleccionado.

### Primary
- **Terracota** ([to be resolved during implementation]): el único acento. Botón de
  acción principal de cada pantalla (buscar, reservar, pagar, publicar), selección
  activa (fechas elegidas en el calendario, filtro aplicado), foco y enlaces
  importantes. Terracota de tierra, anaranjado y algo apagado, nunca rosado.

### Neutral
- **Fondo crema cálido** ([to be resolved during implementation]): superficie
  principal. Teñido apenas hacia la arcilla, nunca blanco puro.
- **Superficie secundaria** ([to be resolved during implementation]): un escalón más
  oscuro o más cálido que el fondo, para paneles laterales, barras de filtros y la
  zona de anfitrión y administrador.
- **Texto tinta cálida** ([to be resolved during implementation]): texto principal.
  Casi negro con un toque de calidez, nunca negro puro.
- **Texto secundario** ([to be resolved during implementation]): metadatos,
  etiquetas y ayudas. Cumple 4.5:1 sobre el fondo.
- **Borde** ([to be resolved during implementation]): divisores y contornos de
  campos. Sutil, cálido, visible a 3:1 cuando delimita un control.

### Semánticos
Los estados de reserva, publicación y disponibilidad necesitan su propio
vocabulario: éxito (confirmada, disponible), advertencia (pendiente, en revisión),
error (rechazada, cancelada, no disponible) e información. Se definen al
implementar, con menos saturación que el acento y siempre acompañados de texto o
ícono.

### Named Rules
**La regla de la recepción.** El terracota ocupa menos del 10% de cualquier
pantalla. Señala la acción principal y lo seleccionado, nada más. Su escasez es lo
que lo hace confiable.

**La regla de la arcilla, no el coral.** El acento es terracota de tierra, cálido y
apagado. Si se confunde con el coral rosado de Airbnb, está mal: bajar el rosado,
subir el ocre.

**La regla de la foto primero.** Donde hay fotografía, la interfaz retrocede: nada
de colores fuertes, sombras ni texto encima de las imágenes que compita con ellas.

## 3. Typography

**Fuente única:** [sans humanista, to be chosen at implementation] (con
`system-ui, sans-serif` de respaldo)

**Character:** una sola voz cálida y muy legible que lleva todo: el nombre del
alojamiento, el precio, el formulario de pago y la tabla del administrador. La
calidez viene de las formas humanistas, no de una segunda familia decorativa.

### Hierarchy
Escala fija en `rem`, sin tamaños fluidos, con razón de 1.2 a 1.25 entre pasos. Los
valores exactos se fijan al implementar.

- **Display:** título de la página de búsqueda y nombre del alojamiento en su
  detalle. Peso alto, el único lugar con tamaño grande.
- **Headline:** títulos de sección (Servicios, Reglas, Política de cancelación,
  Mis reservas).
- **Title:** nombre del alojamiento en los resultados, títulos de paneles y pasos de
  la reserva.
- **Body:** descripciones, reseñas y textos de ayuda. Líneas de 65 a 75 caracteres
  como máximo.
- **Label:** etiquetas de campos, filtros, encabezados de tabla y estados. Nunca en
  mayúsculas sostenidas.

### Named Rules
**La regla del precio legible.** Los montos usan cifras tabulares, el total siempre
pesa más que las líneas del desglose y la moneda se escribe completa en el resumen
final. Un precio nunca va en el tamaño más chico de la pantalla.

**La regla de una sola familia.** Prohibido sumar una segunda tipografía para dar
"personalidad". La jerarquía se construye con tamaño y peso.

## 4. Elevation

Plano por defecto. La profundidad se comunica con capas tonales (fondo y superficie
secundaria) y bordes sutiles, no con sombras. Las únicas sombras aparecen como
respuesta a un estado: un menú desplegado, el selector de fechas abierto o la barra
de resumen de la reserva que queda fija al hacer scroll en el celular. Las
transiciones duran entre 150 y 250 ms con salida exponencial (ease-out-quart), y con
`prefers-reduced-motion` se reemplazan por cambios instantáneos.

### Named Rules
**La regla plana en reposo.** Ninguna superficie lleva sombra mientras nadie
interactúa con ella. Si una tarjeta de resultado tiene sombra en reposo, sobra.

## 6. Do's and Don'ts

### Do:
- **Do** mostrar el precio total desglosado (noches, limpieza, servicio, adicionales,
  descuentos) antes de cualquier botón que comprometa al usuario.
- **Do** acompañar cada color de estado con texto o ícono: "Confirmada", "Pendiente",
  "No disponible". El color solo nunca alcanza (WCAG 2.2 AA).
- **Do** dejar una sola acción principal en terracota por pantalla.
- **Do** dar foco visible en todos los controles y objetivos táctiles de al menos
  24×24 px.
- **Do** usar esqueletos de carga con la forma del contenido, no spinners en el medio
  de la pantalla.
- **Do** escribir estados vacíos que enseñen qué hacer: "Todavía no tienes
  publicaciones. Crea la primera y la revisamos en menos de un día" en lugar de "No
  hay datos".
- **Do** resolver los pasos secundarios en línea o en la misma página antes de pensar
  en un modal.

### Don't:
- **Don't** parecer una **plantilla genérica de IA**: nada de degradados violetas,
  grillas de tarjetas idénticas, íconos dentro de círculos, todo centrado ni métricas
  gigantes con etiquetas diminutas.
- **Don't** parecer un **portal inmobiliario viejo**: sin banners, sin filtros
  eternos visibles a la vez, sin letra chica y sin azul corporativo.
- **Don't** caer en el **Booking agresivo**: prohibida la urgencia falsa ("quedan
  2", contadores regresivos, "14 personas están mirando") y el rojo como presión.
- **Don't** entregar un **panel de admin gris**: las tablas tienen jerarquía y los
  formularios largos se dividen en pasos con guía.
- **Don't** usar bordes laterales de color de más de 1px como acento en tarjetas,
  alertas o filas.
- **Don't** usar texto con degradado ni efectos de vidrio esmerilado decorativos.
- **Don't** usar negro puro ni blanco puro: todo neutro va teñido hacia la arcilla.
- **Don't** animar propiedades de layout ni usar rebotes o efectos elásticos.
- **Don't** usar rayas largas (em dash) en los textos de la interfaz: comas, dos
  puntos o paréntesis.
