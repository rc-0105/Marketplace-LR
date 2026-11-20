-- Dos reservas de la misma publicación no pueden ocupar noches cruzadas (SOF-33, ADR-3).
-- Lo garantiza PostgreSQL, igual que con los periodos disponibles (V15): una verificación
-- en código deja pasar dos solicitudes simultáneas. Solo PENDIENTE y CONFIRMADA ocupan
-- noches; una CANCELADA o VENCIDA libera el rango para que otra reserva lo tome. PostgreSQL no
-- permite now() en la condición de una restricción de exclusión, así que un apartado PENDIENTE
-- agotado sigue ocupando el rango hasta que pasa a VENCIDA: la creación de una reserva y la tarea
-- programada lo hacen, y las consultas de lectura ignoran los vencidos sin esperar a la tarea.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reserva ADD CONSTRAINT reserva_sin_cruce
    EXCLUDE USING gist (publicacion_id WITH =, daterange(llegada, salida, '[)') WITH &&)
    WHERE (estado IN ('PENDIENTE', 'CONFIRMADA'));
