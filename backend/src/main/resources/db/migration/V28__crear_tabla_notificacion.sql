-- Notificaciones dentro de la plataforma (SOF-37, HU-26, RF-40/RF-41): avisos al huésped, al anfitrión o a la
-- persona restringida cuando cambia una reserva, una publicación o su cuenta. Sin correo. El texto se guarda ya
-- armado, con el título y las fechas como estaban al momento del aviso; entidad y entidad_id señalan lo que lo
-- originó. leida_en nula = sin leer. Al borrar un usuario se borran sus notificaciones, igual que sus sesiones
-- invalidadas (V2): en producción las cuentas se anonimizan y nunca se borran. El mensaje admite 600 caracteres
-- porque el de una cuenta restringida lleva el motivo completo, que ya puede tener 500.
CREATE TABLE notificacion (
    id              BIGSERIAL    NOT NULL,
    destinatario_id BIGINT       NOT NULL,
    tipo            VARCHAR(30)  NOT NULL,
    mensaje         VARCHAR(600) NOT NULL,
    entidad         VARCHAR(30)  NOT NULL,
    entidad_id      BIGINT       NOT NULL,
    leida_en        TIMESTAMPTZ,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT notificacion_pk PRIMARY KEY (id),
    CONSTRAINT notificacion_destinatario_fk FOREIGN KEY (destinatario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT notificacion_tipo_check CHECK (tipo IN ('RESERVA_CONFIRMADA', 'RESERVA_RECHAZADA', 'RESERVA_CANCELADA',
        'PUBLICACION_ESTADO', 'CUENTA_RESTRINGIDA'))
);

-- La bandeja de cada usuario: sus notificaciones de la más nueva a la más vieja.
CREATE INDEX ix_notificacion_destinatario_creado_en ON notificacion (destinatario_id, creado_en DESC);
