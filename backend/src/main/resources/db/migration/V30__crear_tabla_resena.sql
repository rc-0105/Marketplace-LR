-- Reseña y calificación de un huésped sobre la reserva COMPLETADA que le dio origen (SOF-80, HU-35, RF-53).
-- La reseña queda asociada al huésped, a la reserva, al alojamiento y a la fecha. El estado de visibilidad
-- nace VISIBLE; la moderación (SOF-82) la pasa a OCULTA sin borrar la fila.
CREATE TABLE resena (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id     BIGINT        NOT NULL REFERENCES reserva (id),
    huesped_id     BIGINT        NOT NULL,
    publicacion_id BIGINT        NOT NULL,
    fecha          TIMESTAMPTZ   NOT NULL,
    calificacion   INTEGER       NOT NULL CHECK (calificacion BETWEEN 1 AND 5),
    contenido      VARCHAR(1000) NOT NULL,
    estado         VARCHAR(10)   NOT NULL DEFAULT 'VISIBLE' CHECK (estado IN ('VISIBLE', 'OCULTA'))
);

-- Una reserva se reseña una sola vez; esta restricción es la que gana si llegan dos solicitudes a la vez.
CREATE UNIQUE INDEX ux_resena_reserva_id ON resena (reserva_id);

-- Las reseñas visibles de una publicación, de la más reciente a la más antigua (SOF-81).
CREATE INDEX ix_resena_publicacion_fecha ON resena (publicacion_id, fecha DESC);
