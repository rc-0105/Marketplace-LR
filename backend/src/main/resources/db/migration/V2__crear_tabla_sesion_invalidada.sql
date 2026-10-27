-- Denylist de sesiones (RF-03). Cada fila es un token cerrado antes de vencer;
-- se identifica por su jti y se borra cuando el token ya no podría aceptarse.
CREATE TABLE sesion_invalidada (
    jti           VARCHAR(36) PRIMARY KEY,
    usuario_id    BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    expira_en     TIMESTAMPTZ NOT NULL,
    invalidado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_sesion_invalidada_expira_en ON sesion_invalidada (expira_en);
