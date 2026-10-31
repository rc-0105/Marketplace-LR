-- Estados de la publicación (SOF-22, RF-14, RF-16, RF-17). V3 solo admitía
-- BORRADOR; solo se amplían los valores, ninguna fila existente cambia.
ALTER TABLE publicacion DROP CONSTRAINT publicacion_estado_check;
ALTER TABLE publicacion ADD CONSTRAINT publicacion_estado_check
    CHECK (estado IN ('BORRADOR', 'PENDIENTE_REVISION', 'ACTIVA', 'PAUSADA'));
