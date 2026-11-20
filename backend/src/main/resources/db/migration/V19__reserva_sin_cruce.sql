-- Dos reservas de la misma publicación no pueden ocupar noches cruzadas (SOF-33, ADR-3).
-- Lo garantiza PostgreSQL, igual que con los periodos disponibles (V15): una verificación
-- en código deja pasar dos solicitudes simultáneas. Solo PENDIENTE y CONFIRMADA ocupan
-- noches; una CANCELADA libera el rango para que otra reserva lo tome.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reserva ADD CONSTRAINT reserva_sin_cruce
    EXCLUDE USING gist (publicacion_id WITH =, daterange(llegada, salida, '[)') WITH &&)
    WHERE (estado IN ('PENDIENTE', 'CONFIRMADA'));
