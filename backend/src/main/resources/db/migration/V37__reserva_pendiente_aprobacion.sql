-- Aprobación de reservas (SOF-79, HU-34, RF-52). Una publicación con modo de reserva por aprobación recibe una
-- solicitud: la reserva nace PENDIENTE_APROBACION y aparta las noches hasta vence_en (la ventana de aprobación
-- del anfitrión, marketplace.reservas.horas-aprobacion) igual que una PENDIENTE. Al aprobarla pasa a PENDIENTE con
-- un apartado nuevo para que el huésped pague (el pago la confirma, ADR-4); si el anfitrión la rechaza queda
-- RECHAZADA con su motivo, y si la ventana se agota pasa a VENCIDA. 'PENDIENTE_APROBACION' mide justo 20
-- caracteres, lo que admite la columna estado (V21), así que no hace falta ensancharla.
ALTER TABLE reserva ADD COLUMN motivo_rechazo VARCHAR(500);

-- El CHECK de estado conserva el nombre de V21 y los estados que tenía tras V31, con los dos nuevos. El motivo es
-- obligatorio mientras esté RECHAZADA, igual que el de una publicación bloqueada (V31).
ALTER TABLE reserva DROP CONSTRAINT reserva_estado_check,
    ADD CONSTRAINT reserva_estado_check
        CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'VENCIDA', 'COMPLETADA', 'EN_REVISION_MANUAL',
                          'PENDIENTE_APROBACION', 'RECHAZADA')),
    ADD CONSTRAINT reserva_motivo_rechazo_check
        CHECK (estado <> 'RECHAZADA' OR (motivo_rechazo IS NOT NULL AND length(btrim(motivo_rechazo)) > 0));

-- La restricción de exclusión de V22/V31 se recrea igual (ADR-3 intacto), con PENDIENTE_APROBACION en su condición:
-- una solicitud vigente aparta sus noches. RECHAZADA, como CANCELADA y VENCIDA, las libera.
ALTER TABLE reserva DROP CONSTRAINT reserva_sin_cruce;
ALTER TABLE reserva ADD CONSTRAINT reserva_sin_cruce
    EXCLUDE USING gist (publicacion_id WITH =, daterange(llegada, salida, '[)') WITH &&)
    WHERE (estado IN ('PENDIENTE', 'CONFIRMADA', 'EN_REVISION_MANUAL', 'PENDIENTE_APROBACION'));

-- Las cuatro notificaciones de la solicitud (cada tipo mide a lo sumo 30 caracteres, notificacion.tipo de V28).
ALTER TABLE notificacion DROP CONSTRAINT notificacion_tipo_check;

ALTER TABLE notificacion ADD CONSTRAINT notificacion_tipo_check CHECK (tipo IN (
    'RESERVA_CONFIRMADA', 'RESERVA_RECHAZADA', 'RESERVA_CANCELADA', 'PUBLICACION_ESTADO', 'CUENTA_RESTRINGIDA',
    'RESENA_OCULTA', 'SOLICITUD_RECIBIDA', 'SOLICITUD_APROBADA', 'SOLICITUD_RECHAZADA', 'SOLICITUD_VENCIDA'));
