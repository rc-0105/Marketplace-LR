-- Publicaciones de alojamiento (RF-11, RF-12). Nacen en BORRADOR; los demás
-- estados llegan con SOF-22. Los campos opcionales admiten nulo porque un
-- borrador puede guardarse incompleto, pero si llegan deben ser válidos.
CREATE TABLE publicacion (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    anfitrion_id BIGINT        NOT NULL REFERENCES usuario (id),
    titulo       VARCHAR(120)  NOT NULL,
    descripcion  VARCHAR(2000),
    ciudad       VARCHAR(100),
    direccion    VARCHAR(200),
    tipo         VARCHAR(20)   CHECK (tipo IN ('CASA', 'APARTAMENTO', 'HABITACION')),
    capacidad    INTEGER       CHECK (capacidad > 0),
    precio_noche NUMERIC(12, 2) CHECK (precio_noche > 0),
    reglas       VARCHAR(2000),
    estado       VARCHAR(20)   NOT NULL CHECK (estado IN ('BORRADOR')),
    creado_en    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_publicacion_anfitrion_id ON publicacion (anfitrion_id);

CREATE TABLE publicacion_servicio (
    publicacion_id BIGINT      NOT NULL REFERENCES publicacion (id) ON DELETE CASCADE,
    servicio       VARCHAR(60) NOT NULL,
    PRIMARY KEY (publicacion_id, servicio)
);

-- La posición conserva el orden en que el anfitrión cargó las imágenes.
CREATE TABLE publicacion_imagen (
    publicacion_id BIGINT       NOT NULL REFERENCES publicacion (id) ON DELETE CASCADE,
    posicion       INTEGER      NOT NULL CHECK (posicion >= 0),
    url            VARCHAR(500) NOT NULL,
    PRIMARY KEY (publicacion_id, posicion)
);
