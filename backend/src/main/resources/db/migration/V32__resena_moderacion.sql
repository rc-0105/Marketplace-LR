-- Moderación de reseñas inapropiadas (SOF-82, HU-37, RF-55). Ocultar una reseña no la borra: solo cambia su
-- estado a OCULTA y guarda el motivo que dio el administrador. Las reseñas existentes siguen VISIBLES.
ALTER TABLE resena ADD COLUMN motivo_ocultacion VARCHAR(500);

ALTER TABLE resena ADD CONSTRAINT ck_resena_oculta_con_motivo
    CHECK (estado = 'VISIBLE' OR motivo_ocultacion IS NOT NULL);
