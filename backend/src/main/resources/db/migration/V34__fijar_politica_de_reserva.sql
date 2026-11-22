-- Las reservas anteriores conservan NULL: la política actual de la publicación
-- no demuestra qué condiciones estaban vigentes cuando se reservaron.
ALTER TABLE reserva
    ADD COLUMN politica_nombre VARCHAR(40),
    ADD COLUMN politica_descripcion VARCHAR(300),
    ADD COLUMN politica_dias_sin_costo INTEGER,
    ADD COLUMN politica_porcentaje_devolucion INTEGER;

CREATE FUNCTION fijar_politica_de_reserva() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF (NEW.politica_nombre, NEW.politica_descripcion, NEW.politica_dias_sin_costo, NEW.politica_porcentaje_devolucion)
            IS DISTINCT FROM
           (OLD.politica_nombre, OLD.politica_descripcion, OLD.politica_dias_sin_costo, OLD.politica_porcentaje_devolucion) THEN
            RAISE EXCEPTION 'La política de una reserva no se puede modificar' USING ERRCODE = '23514';
        END IF;
    ELSE
        -- Una sola lectura en la inserción; sin política, los cuatro campos quedan NULL.
        SELECT c.nombre, c.descripcion, c.dias_sin_costo, c.porcentaje_devolucion
          INTO NEW.politica_nombre, NEW.politica_descripcion, NEW.politica_dias_sin_costo, NEW.politica_porcentaje_devolucion
          FROM publicacion p JOIN politica_cancelacion c ON c.id = p.politica_id
         WHERE p.id = NEW.publicacion_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER fijar_politica_de_reserva
    BEFORE INSERT OR UPDATE OF politica_nombre, politica_descripcion, politica_dias_sin_costo, politica_porcentaje_devolucion
    ON reserva FOR EACH ROW EXECUTE FUNCTION fijar_politica_de_reserva();
