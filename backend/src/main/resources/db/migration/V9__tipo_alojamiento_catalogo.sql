-- El tipo de alojamiento pasa de texto con CHECK a un catálogo (RF-18), igual
-- que los servicios: así se agregan tipos sin migrar y la búsqueda filtra por id.
-- Conserva el tipo de las publicaciones que ya existían.

CREATE TABLE tipo_alojamiento (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(40) NOT NULL UNIQUE
);

INSERT INTO tipo_alojamiento (nombre) VALUES ('Apartamento'), ('Casa'), ('Habitación');

ALTER TABLE publicacion ADD COLUMN tipo_id BIGINT REFERENCES tipo_alojamiento (id);

UPDATE publicacion p
SET tipo_id = t.id
FROM tipo_alojamiento t
WHERE t.nombre = CASE p.tipo
                     WHEN 'CASA' THEN 'Casa'
                     WHEN 'APARTAMENTO' THEN 'Apartamento'
                     WHEN 'HABITACION' THEN 'Habitación'
                 END;

-- Junto con la columna se va su CHECK.
ALTER TABLE publicacion DROP COLUMN tipo;

CREATE INDEX ix_publicacion_tipo_id ON publicacion (tipo_id);
