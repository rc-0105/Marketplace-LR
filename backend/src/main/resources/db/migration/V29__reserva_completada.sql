-- Cierre de reservas (SOF-78, HU-33, RF-50): una CONFIRMADA cuya fecha de salida ya pasó queda
-- COMPLETADA, lo que habilita la reseña (otro épica, todavía sin empezar) y la saca de la
-- restricción de exclusión de V22 (que solo cubre PENDIENTE y CONFIRMADA), igual que CANCELADA y
-- VENCIDA.
ALTER TABLE reserva DROP CONSTRAINT reserva_estado_check;
ALTER TABLE reserva
    ADD CONSTRAINT reserva_estado_check
        CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'VENCIDA', 'COMPLETADA'));
