-- Conciliación de pagos (SOF-34): un pago en ERROR (la pasarela no respondió a tiempo, o un reembolso
-- falló) lo resuelve una tarea programada preguntándole a la pasarela si cobró. REEMBOLSADO es un cobro que
-- se devolvió porque la reserva ya no estaba PENDIENTE, y guarda su transacción; NO_COBRADO es un ERROR en el
-- que la pasarela nunca cobró.
ALTER TABLE reserva DROP CONSTRAINT reserva_estado_pago_check;
ALTER TABLE reserva
    ADD CONSTRAINT reserva_estado_pago_check
        CHECK (estado_pago IN ('SIN_PAGO', 'APROBADO', 'RECHAZADO', 'ERROR', 'REEMBOLSADO', 'NO_COBRADO'));

ALTER TABLE reserva DROP CONSTRAINT reserva_transaccion_check;
ALTER TABLE reserva
    ADD CONSTRAINT reserva_transaccion_check
        CHECK ((estado_pago IN ('APROBADO', 'REEMBOLSADO')) = (transaccion_id IS NOT NULL));

-- La conciliación recorre solo los pagos en ERROR, que son pocos.
CREATE INDEX ix_reserva_estado_pago_error ON reserva (id) WHERE estado_pago = 'ERROR';
