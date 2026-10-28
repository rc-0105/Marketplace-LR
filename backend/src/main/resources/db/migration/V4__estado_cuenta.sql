-- Estados de cuenta (RF-06). Una cuenta restringida siempre guarda el motivo que
-- escribió el administrador. El CHECK de estado conserva el nombre que le dio V1.
ALTER TABLE usuario ADD COLUMN motivo_restriccion VARCHAR(500);

ALTER TABLE usuario DROP CONSTRAINT usuario_estado_check,
    ADD CONSTRAINT usuario_estado_check CHECK (estado IN ('ACTIVA', 'DESACTIVADA', 'RESTRINGIDA')),
    ADD CONSTRAINT usuario_motivo_restriccion_check
        CHECK (estado <> 'RESTRINGIDA' OR motivo_restriccion IS NOT NULL);
