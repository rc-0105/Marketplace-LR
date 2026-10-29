-- Rastro de la última edición de una publicación (RF-13). Nulos mientras nadie
-- la haya editado; editado_por puede ser el anfitrión dueño o un administrador.
ALTER TABLE publicacion
    ADD COLUMN editado_por BIGINT REFERENCES usuario (id),
    ADD COLUMN editado_en  TIMESTAMPTZ;
