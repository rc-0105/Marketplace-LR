-- Reservas sobre una cotización vigente (SOF-33, RF-35, RF-36). El precio queda fijo en el
-- momento de confirmar: una reserva no recalcula si luego cambian las tarifas de la publicación.
-- Rango semiabierto [llegada, salida), igual que periodo_disponible: el día de salida no
-- cuenta como noche ocupada, así que dos reservas contiguas no se cruzan.
--
-- Estados (ADR-4): una reserva nace PENDIENTE y aparta las noches solo hasta vence_en (por defecto
-- 15 minutos, marketplace.reservas.minutos-apartado); el pago, que es aparte, la pasa a CONFIRMADA.
-- Un apartado que se agota pasa a VENCIDA (la tarea programada de ConfiguracionTareas lo hace cada minuto
-- y la creación libera antes los que cruzan), y VENCIDA, igual que CANCELADA, libera las noches.
CREATE TABLE reserva (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    publicacion_id              BIGINT         NOT NULL REFERENCES publicacion (id),
    huesped_id                  BIGINT         NOT NULL REFERENCES usuario (id),
    llegada                     DATE           NOT NULL,
    salida                      DATE           NOT NULL,
    estado                      VARCHAR(20)    NOT NULL CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'VENCIDA')),
    noches                      INTEGER        NOT NULL CHECK (noches > 0),
    precio_base                 NUMERIC(12, 2) NOT NULL,
    tarifa_limpieza             NUMERIC(12, 2) NOT NULL,
    tarifa_servicio             NUMERIC(12, 2) NOT NULL,
    descuento                   NUMERIC(12, 2) NOT NULL,
    total_servicios_adicionales NUMERIC(12, 2) NOT NULL,
    total                       NUMERIC(12, 2) NOT NULL,
    -- Servicios adicionales elegidos: sin ellos un reintento con otros extras se confundiría con la
    -- misma reserva (ADR-4). Se guardan los ids, sin llave foránea: un servicio puede borrarse luego.
    servicios_adicionales_ids   BIGINT[]       NOT NULL DEFAULT '{}',
    vence_en                    TIMESTAMPTZ    NOT NULL,
    creado_en                   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT reserva_fechas_check CHECK (salida > llegada)
);

CREATE INDEX ix_reserva_publicacion_id ON reserva (publicacion_id);
CREATE INDEX ix_reserva_huesped_id ON reserva (huesped_id);
