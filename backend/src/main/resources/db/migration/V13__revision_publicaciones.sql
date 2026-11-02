-- Revisión administrativa de publicaciones (SOF-23, RF-15). Solo se agregan
-- columnas que admiten nulo; ninguna fila existente cambia.
-- observaciones_revision: motivo con que el administrador devuelve la publicación a BORRADOR.
-- enviada_revision_en: cuándo pidió el anfitrión la revisión; ordena el panel por antigüedad.
ALTER TABLE publicacion ADD COLUMN observaciones_revision VARCHAR(1000);
ALTER TABLE publicacion ADD COLUMN enviada_revision_en TIMESTAMPTZ;
