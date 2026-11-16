-- Búsqueda de alojamientos por ciudad (SOF-28, RF-28, RF-30). La consulta filtra por
-- estado ACTIVA y por la ciudad sin distinguir mayúsculas (Hibernate la compara con upper), y los filtros de SOF-29
-- suman el tipo, así que el índice sigue ese orden. No cambia ningún dato.
CREATE INDEX ix_publicacion_busqueda ON publicacion (estado, upper(ciudad), tipo_id);
