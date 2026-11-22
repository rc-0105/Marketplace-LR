-- Cambia el catálogo de nuevas reservas, nunca los términos ya aceptados.
UPDATE politica_cancelacion
   SET dias_sin_costo = 2, porcentaje_devolucion = 50,
       descripcion = 'Devolución del 100% con 48 horas o más de anticipación a la llegada; con menos de 48 horas, del 50%.'
 WHERE nombre = 'Flexible';

ALTER TABLE cancelacion
    ADD COLUMN resultado_reembolso VARCHAR(20) CHECK (resultado_reembolso IN ('NO_APLICA', 'SIMULADO', 'REVISION_MANUAL')),
    ADD COLUMN reembolso_id VARCHAR(80),
    ADD COLUMN error_reembolso VARCHAR(40);
