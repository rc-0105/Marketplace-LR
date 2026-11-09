-- Dos periodos disponibles de una misma publicación no pueden cruzarse (SOF-25, RF-23).
-- Lo garantiza PostgreSQL, igual que ADR-3 con las reservas: una verificación en código
-- deja pasar dos solicitudes simultáneas. Rango semiabierto: dos periodos contiguos no se cruzan.
-- btree_gist permite comparar publicacion_id con = dentro de un índice GiST; desde
-- PostgreSQL 13 es una extensión confiable que el dueño de la base puede instalar.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE periodo_disponible ADD CONSTRAINT periodo_disponible_sin_cruce
    EXCLUDE USING gist (publicacion_id WITH =, daterange(fecha_inicio, fecha_fin, '[)') WITH &&);
