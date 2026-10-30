-- Condiciones comerciales de la publicación (RF-20, RF-21, RF-22), según el
-- modelo relacional: política de cancelación del catálogo, modo de reserva,
-- tarifa de limpieza y servicios adicionales con costo.

-- Cancelar con al menos dias_sin_costo días de anticipación devuelve el 100 %;
-- después se devuelve porcentaje_devolucion. La devolución la calcula RF-47.
CREATE TABLE politica_cancelacion (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre                VARCHAR(40)  NOT NULL UNIQUE,
    descripcion           VARCHAR(300) NOT NULL,
    dias_sin_costo        INTEGER      NOT NULL CHECK (dias_sin_costo >= 0),
    porcentaje_devolucion INTEGER      NOT NULL CHECK (porcentaje_devolucion BETWEEN 0 AND 100)
);

INSERT INTO politica_cancelacion (nombre, descripcion, dias_sin_costo, porcentaje_devolucion) VALUES
    ('Flexible', 'Devolución total si cancelas hasta 1 día antes de la llegada; después, del 50 %.', 1, 50),
    ('Moderada', 'Devolución total si cancelas hasta 5 días antes de la llegada; después, del 50 %.', 5, 50),
    ('Estricta', 'Devolución total si cancelas hasta 14 días antes de la llegada; después, no hay devolución.', 14, 0);

-- Las publicaciones que ya existen quedan con reserva inmediata, el valor por defecto (RF-21).
ALTER TABLE publicacion
    ADD COLUMN tarifa_limpieza NUMERIC(12, 2) CHECK (tarifa_limpieza >= 0),
    ADD COLUMN modo_reserva VARCHAR(20) NOT NULL DEFAULT 'INMEDIATA'
        CHECK (modo_reserva IN ('INMEDIATA', 'APROBACION')),
    ADD COLUMN politica_id BIGINT REFERENCES politica_cancelacion (id);

CREATE INDEX ix_publicacion_politica_id ON publicacion (politica_id);

-- Propios de cada publicación, a diferencia de los servicios del catálogo. La
-- cotización (HU-20) los referencia, por eso cada uno conserva su id al editar.
CREATE TABLE servicio_adicional (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    publicacion_id BIGINT         NOT NULL REFERENCES publicacion (id) ON DELETE CASCADE,
    nombre         VARCHAR(80)    NOT NULL,
    costo          NUMERIC(12, 2) NOT NULL CHECK (costo > 0)
);

-- Un nombre por publicación, sin importar mayúsculas: el servicio los compara así.
CREATE UNIQUE INDEX uq_servicio_adicional_nombre ON servicio_adicional (publicacion_id, lower(nombre));
