-- Periodos en que el anfitrión ofrece su alojamiento (SOF-25, RF-23, RF-24).
-- Rango semiabierto [fecha_inicio, fecha_fin): la fecha final es el día de salida,
-- así que dos periodos contiguos comparten ese día sin cruzarse.
-- Borrar una publicación (solo un borrador) borra también sus periodos.
CREATE TABLE periodo_disponible (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    publicacion_id BIGINT      NOT NULL REFERENCES publicacion (id) ON DELETE CASCADE,
    fecha_inicio   DATE        NOT NULL,
    fecha_fin      DATE        NOT NULL,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT periodo_disponible_fechas_check CHECK (fecha_fin > fecha_inicio)
);

CREATE INDEX ix_periodo_disponible_publicacion_id ON periodo_disponible (publicacion_id);
