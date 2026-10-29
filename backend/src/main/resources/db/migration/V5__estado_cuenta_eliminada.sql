-- Cuentas eliminadas por su dueño (RF-10): la fila se anonimiza y nunca se borra,
-- para no romper las FK ni la auditoría. El CHECK de estado conserva el nombre de V1.
ALTER TABLE usuario DROP CONSTRAINT usuario_estado_check,
    ADD CONSTRAINT usuario_estado_check
        CHECK (estado IN ('ACTIVA', 'DESACTIVADA', 'RESTRINGIDA', 'ELIMINADA')),
    ADD CONSTRAINT usuario_eliminada_anonima_check
        CHECK (estado <> 'ELIMINADA' OR (nombre = 'Usuario eliminado'
            AND correo = 'eliminada-' || CAST(id AS TEXT) || '@eliminada.invalid'
            AND motivo_restriccion IS NULL));
