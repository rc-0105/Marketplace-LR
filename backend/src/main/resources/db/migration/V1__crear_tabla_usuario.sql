-- Cuentas de la plataforma (RF-01). El correo se guarda normalizado en
-- minúsculas, así que el índice único basta para impedir duplicados.
CREATE TABLE usuario (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    correo          VARCHAR(254) NOT NULL,
    contrasena_hash VARCHAR(100) NOT NULL,
    rol             VARCHAR(20)  NOT NULL CHECK (rol IN ('HUESPED', 'ANFITRION', 'ADMINISTRADOR')),
    estado          VARCHAR(20)  NOT NULL CHECK (estado IN ('ACTIVA')),
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_usuario_correo ON usuario (correo);
