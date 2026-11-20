-- Registro persistente de eventos críticos (RF-59, HU-40). Es de solo inserción: el trigger
-- rechaza UPDATE y DELETE; TRUNCATE queda libre para las pruebas. Sin FK a usuario ni CHECK
-- en accion: los publicadores futuros agregan valores sin migración.
CREATE TABLE evento_auditoria (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha       TIMESTAMPTZ  NOT NULL,
    usuario     VARCHAR(64),
    accion      VARCHAR(40)  NOT NULL,
    entidad     VARCHAR(60)  NOT NULL,
    entidad_id  VARCHAR(64),
    correlacion VARCHAR(64),
    resultado   VARCHAR(10)  NOT NULL
        CONSTRAINT evento_auditoria_resultado_check CHECK (resultado IN ('EXITO', 'FALLO')),
    datos       JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX ix_evento_auditoria_fecha ON evento_auditoria (fecha DESC, id DESC);
CREATE INDEX ix_evento_auditoria_usuario_fecha ON evento_auditoria (usuario, fecha DESC);
CREATE INDEX ix_evento_auditoria_accion_fecha ON evento_auditoria (accion, fecha DESC);
CREATE INDEX ix_evento_auditoria_entidad_fecha ON evento_auditoria (entidad, entidad_id, fecha DESC);
CREATE INDEX ix_evento_auditoria_correlacion ON evento_auditoria (correlacion);

CREATE FUNCTION evento_auditoria_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'evento_auditoria es de solo insercion: % no permitido', TG_OP;
END
$$;

CREATE TRIGGER evento_auditoria_sin_cambios
    BEFORE UPDATE OR DELETE ON evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION evento_auditoria_inmutable();
