-- Cancelación de una reserva CONFIRMADA por su huésped o por el anfitrión de la publicación (SOF-75,
-- HU-30, RF-46/RF-48). El valor de devolución es siempre el 100% del total de la reserva; la política de
-- cancelación por anticipación (porcentajes parciales según días de anticipación) es SOF-76, todavía sin
-- empezar, por eso el estado final queda angosto a CANCELADA: esa historia va a ampliar este check con otra
-- migración cuando agregue más desenlaces.
CREATE TABLE cancelacion (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id       BIGINT         NOT NULL REFERENCES reserva (id),
    usuario_id       BIGINT         NOT NULL,
    fecha            TIMESTAMPTZ    NOT NULL,
    motivo           VARCHAR(500)   NOT NULL,
    valor_devolucion NUMERIC(12, 2) NOT NULL,
    estado_final     VARCHAR(20)    NOT NULL CHECK (estado_final IN ('CANCELADA'))
);

-- Una reserva no se cancela dos veces.
CREATE UNIQUE INDEX ux_cancelacion_reserva_id ON cancelacion (reserva_id);
