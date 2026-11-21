-- Al ocultar una reseña, el huésped autor recibe una notificación con el motivo (SOF-82, HU-37).
ALTER TABLE notificacion DROP CONSTRAINT notificacion_tipo_check;

ALTER TABLE notificacion ADD CONSTRAINT notificacion_tipo_check CHECK (tipo IN (
    'RESERVA_CONFIRMADA', 'RESERVA_RECHAZADA', 'RESERVA_CANCELADA', 'PUBLICACION_ESTADO', 'CUENTA_RESTRINGIDA',
    'RESENA_OCULTA'));
