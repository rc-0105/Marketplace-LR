-- Pago de la reserva con la pasarela en modo prueba (SOF-34, RF-37). Las reservas existentes quedan
-- SIN_PAGO. APROBADO va con la reserva CONFIRMADA y el identificador de la transacción de la pasarela;
-- RECHAZADO la pasa a CANCELADA, lo que libera las noches (la exclusión de V22 solo cubre PENDIENTE y
-- CONFIRMADA); ERROR (la pasarela no respondió a tiempo o falló) la deja PENDIENTE hasta que se reintente
-- o venza su apartado.
ALTER TABLE reserva
    ADD COLUMN estado_pago    VARCHAR(20)  NOT NULL DEFAULT 'SIN_PAGO'
        CHECK (estado_pago IN ('SIN_PAGO', 'APROBADO', 'RECHAZADO', 'ERROR')),
    ADD COLUMN transaccion_id VARCHAR(255);

-- Solo un pago aprobado tiene transacción, y una transacción no confirma dos reservas.
ALTER TABLE reserva
    ADD CONSTRAINT reserva_transaccion_check CHECK ((estado_pago = 'APROBADO') = (transaccion_id IS NOT NULL));

CREATE UNIQUE INDEX ux_reserva_transaccion_id ON reserva (transaccion_id) WHERE transaccion_id IS NOT NULL;
