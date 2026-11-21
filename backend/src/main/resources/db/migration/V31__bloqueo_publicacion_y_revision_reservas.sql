-- Bloqueo de una publicación engañosa y revisión manual de sus reservas (SOF-83, HU-38, RF-56/RF-57).
-- Un administrador bloquea una publicación ACTIVA o PAUSADA con un motivo: pasa a BLOQUEADA, sale de las
-- búsquedas y su anfitrión no puede volver a activarla. El motivo es obligatorio mientras esté bloqueada,
-- igual que el de una cuenta restringida (V4). Las filas existentes siguen siendo válidas: ninguna está
-- BLOQUEADA y las columnas nuevas admiten nulos.
ALTER TABLE publicacion
    ADD COLUMN motivo_bloqueo VARCHAR(500),
    ADD COLUMN bloqueada_en   TIMESTAMPTZ;

ALTER TABLE publicacion DROP CONSTRAINT publicacion_estado_check,
    ADD CONSTRAINT publicacion_estado_check
        CHECK (estado IN ('BORRADOR', 'PENDIENTE_REVISION', 'ACTIVA', 'PAUSADA', 'BLOQUEADA')),
    ADD CONSTRAINT publicacion_motivo_bloqueo_check
        CHECK (estado <> 'BLOQUEADA' OR motivo_bloqueo IS NOT NULL);

-- Las reservas futuras CONFIRMADA de la publicación bloqueada pasan a EN_REVISION_MANUAL hasta que el
-- administrador las confirme o las cancele con devolución. Mientras tanto siguen ocupando sus noches: la
-- restricción de exclusión de V22 se recrea igual, con el estado nuevo en su condición. El CHECK de estado
-- conserva el nombre que PostgreSQL le dio en V21 y los estados que tenía tras V29 (COMPLETADA, SOF-78).
ALTER TABLE reserva DROP CONSTRAINT IF EXISTS reserva_estado_check,
    ADD CONSTRAINT reserva_estado_check
        CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'VENCIDA', 'COMPLETADA', 'EN_REVISION_MANUAL'));

ALTER TABLE reserva DROP CONSTRAINT reserva_sin_cruce;
ALTER TABLE reserva ADD CONSTRAINT reserva_sin_cruce
    EXCLUDE USING gist (publicacion_id WITH =, daterange(llegada, salida, '[)') WITH &&)
    WHERE (estado IN ('PENDIENTE', 'CONFIRMADA', 'EN_REVISION_MANUAL'));
