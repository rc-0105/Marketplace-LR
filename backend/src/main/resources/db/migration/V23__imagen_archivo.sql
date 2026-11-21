-- Archivos de imagen subidos por los anfitriones (RF-19). Se guardan en la base de datos y no en disco
-- porque el contenedor de Render no conserva el sistema de archivos entre despliegues. La publicación
-- guarda solo la URL pública (/api/v1/imagenes/{id}), así que cambiar de almacenamiento no toca el resto.
-- El tipo se decide por el contenido del archivo, nunca por la extensión que declara el cliente.
CREATE TABLE imagen_archivo (
    id             UUID         PRIMARY KEY,
    publicacion_id BIGINT       NOT NULL REFERENCES publicacion (id) ON DELETE CASCADE,
    tipo           VARCHAR(10)  NOT NULL CHECK (tipo IN ('PNG', 'JPEG')),
    contenido      BYTEA        NOT NULL,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_imagen_archivo_publicacion_id ON imagen_archivo (publicacion_id);
