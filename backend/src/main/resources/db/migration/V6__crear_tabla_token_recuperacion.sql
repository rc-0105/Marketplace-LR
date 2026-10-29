-- Códigos de recuperación de contraseña (RF-05). Se guarda solo el hash SHA-256
-- del código; usado_en marca el uso (o la anulación) y lo vuelve inservible.
CREATE TABLE token_recuperacion (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id        BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    codigo_hash       VARCHAR(64) NOT NULL,
    vence_en          TIMESTAMPTZ NOT NULL,
    usado_en          TIMESTAMPTZ,
    intentos_fallidos INTEGER     NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0),
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_token_recuperacion_usuario_id ON token_recuperacion (usuario_id);
