-- Claves de idempotencia (SOF-77, HU-32): una fila por (usuario, Idempotency-Key). Guarda la huella de la
-- solicitud y, al terminar bien, la respuesta exacta para replicarla. Sin clave foránea a usuario: la fila es
-- efímera (vigencia de 24 h) y la limpia una tarea programada. El cuerpo es TEXT y no JSONB porque JSONB
-- reordena las claves y la réplica dejaría de ser idéntica byte a byte. "reclamo" identifica cada intento:
-- evita que uno lento complete o libere la fila que otro ya tomó.
CREATE TABLE clave_idempotencia (
    usuario_id    BIGINT       NOT NULL,
    clave         UUID         NOT NULL,
    huella        VARCHAR(64)  NOT NULL,
    estado        VARCHAR(12)  NOT NULL
        CONSTRAINT clave_idempotencia_estado_check CHECK (estado IN ('EN_PROCESO', 'COMPLETADA')),
    reclamo       UUID         NOT NULL,
    codigo_estado SMALLINT,
    cuerpo        TEXT,
    creada_en     TIMESTAMPTZ  NOT NULL,
    vence_en      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT clave_idempotencia_pk PRIMARY KEY (usuario_id, clave),
    CONSTRAINT clave_idempotencia_completa_check
        CHECK (estado = 'EN_PROCESO' OR (codigo_estado IS NOT NULL AND cuerpo IS NOT NULL))
);

CREATE INDEX ix_clave_idempotencia_vence_en ON clave_idempotencia (vence_en);
