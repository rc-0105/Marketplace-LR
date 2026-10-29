-- Servicios e imágenes pasan a ser relaciones, como en el modelo relacional:
-- un catálogo de servicios con su tabla intermedia (N:M) y una fila con
-- identidad propia por imagen (1:N). Conserva los datos que ya existían.
-- Las restricciones únicas son diferidas porque, al editar, Hibernate puede
-- insertar o mover filas antes de borrar las viejas en la misma transacción
-- (por ejemplo, dos imágenes que intercambian su posición).

CREATE TABLE servicio (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL UNIQUE
);

-- "Wifi" y "wifi" son el mismo servicio: el catálogo no admite dos que solo
-- cambien en mayúsculas.
CREATE UNIQUE INDEX uq_servicio_nombre_sin_mayusculas ON servicio (lower(nombre));

INSERT INTO servicio (nombre) VALUES
    ('Wifi'), ('Cocina'), ('Parqueadero'), ('Piscina'), ('Aire acondicionado'),
    ('Calefacción'), ('Lavadora'), ('Televisión'), ('Zona de trabajo'), ('Se admiten mascotas');

-- Los servicios que ya se escribieron como texto entran al catálogo para no
-- perderlos, uno por nombre sin importar mayúsculas ni espacios.
INSERT INTO servicio (nombre)
SELECT DISTINCT ON (lower(trim(ps.servicio))) trim(ps.servicio)
FROM publicacion_servicio ps
WHERE NOT EXISTS (SELECT 1 FROM servicio s WHERE lower(s.nombre) = lower(trim(ps.servicio)))
ORDER BY lower(trim(ps.servicio)), ps.servicio;

ALTER TABLE publicacion_servicio ADD COLUMN servicio_id BIGINT REFERENCES servicio (id);

UPDATE publicacion_servicio ps
SET servicio_id = s.id
FROM servicio s
WHERE lower(s.nombre) = lower(trim(ps.servicio));

-- Si una publicación tenía "wifi" y "Wifi", ahora apuntan al mismo servicio:
-- queda una sola fila.
DELETE FROM publicacion_servicio a
USING publicacion_servicio b
WHERE a.publicacion_id = b.publicacion_id
  AND a.servicio_id = b.servicio_id
  AND a.ctid > b.ctid;

ALTER TABLE publicacion_servicio DROP CONSTRAINT publicacion_servicio_pkey;
ALTER TABLE publicacion_servicio DROP COLUMN servicio;
ALTER TABLE publicacion_servicio ALTER COLUMN servicio_id SET NOT NULL;
ALTER TABLE publicacion_servicio ADD COLUMN id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY;
ALTER TABLE publicacion_servicio
    ADD CONSTRAINT uq_publicacion_servicio UNIQUE (publicacion_id, servicio_id) DEFERRABLE INITIALLY DEFERRED;

-- Cada imagen pasa a tener su propio id y conserva la posición: define el orden
-- y la imagen principal, la de posición 0 (RF-19).
ALTER TABLE publicacion_imagen DROP CONSTRAINT publicacion_imagen_pkey;
ALTER TABLE publicacion_imagen ADD COLUMN id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY;
ALTER TABLE publicacion_imagen
    ADD CONSTRAINT uq_publicacion_imagen_posicion UNIQUE (publicacion_id, posicion) DEFERRABLE INITIALLY DEFERRED;
