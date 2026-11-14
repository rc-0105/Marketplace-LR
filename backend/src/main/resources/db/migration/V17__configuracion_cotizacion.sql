-- Porcentajes con que se calcula la cotización (RF-32). Una sola fila: la tarifa de servicio
-- y el descuento se aplican sobre el precio base de la estancia. SOF-32 (RF-34) los vuelve
-- editables por el administrador; mientras tanto la tarifa de servicio es del 10 % y no hay descuento.
CREATE TABLE configuracion_cotizacion (
    id                   SMALLINT     PRIMARY KEY CHECK (id = 1),
    porcentaje_servicio  NUMERIC(5, 2) NOT NULL CHECK (porcentaje_servicio BETWEEN 0 AND 100),
    porcentaje_descuento NUMERIC(5, 2) NOT NULL CHECK (porcentaje_descuento BETWEEN 0 AND 100)
);

INSERT INTO configuracion_cotizacion (id, porcentaje_servicio, porcentaje_descuento) VALUES (1, 10, 0);
