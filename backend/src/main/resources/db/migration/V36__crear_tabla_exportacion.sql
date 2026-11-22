-- Registro de cada exportación de reportes del administrador (SOF-39, HU-28, RF-44): quién la
-- pidió, cuándo, qué reporte, en qué formato y con qué filtros. desde y hasta nulos = rango
-- abierto por ese lado. Sin FK a usuario, igual que evento_auditoria (V20): el registro debe
-- sobrevivir a la cuenta, por eso guarda también el correo que tenía al exportar.
CREATE TABLE exportacion (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    administrador_id     BIGINT       NOT NULL,
    administrador_correo VARCHAR(254) NOT NULL,
    tipo                 VARCHAR(20)  NOT NULL
        CONSTRAINT exportacion_tipo_check
            CHECK (tipo IN ('USUARIOS', 'PUBLICACIONES', 'RESERVAS', 'INGRESOS', 'DEMANDA')),
    formato              VARCHAR(4)   NOT NULL
        CONSTRAINT exportacion_formato_check CHECK (formato IN ('PDF', 'XLSX')),
    desde                DATE,
    hasta                DATE,
    filas                INTEGER      NOT NULL CHECK (filas >= 0),
    creado_en            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_exportacion_creado_en ON exportacion (creado_en DESC);
