package co.edu.unbosque.marketplace.auditoria;

import org.springframework.jdbc.core.JdbcTemplate;

/** Siembra la tabla evento_auditoria con las filas base de la spec (A1 a A7, con ids 1 a 7 tras TRUNCATE). */
public final class SemillaAuditoria {

    private static final String INSERTAR = "INSERT INTO evento_auditoria "
            + "(fecha, usuario, accion, entidad, entidad_id, resultado, datos, correlacion) "
            + "VALUES (?::timestamptz, ?, ?, ?, ?, ?, jsonb_build_object('operacion', ?::text), ?)";

    private final JdbcTemplate jdbc;

    public SemillaAuditoria(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void vaciar() {
        jdbc.execute("TRUNCATE evento_auditoria RESTART IDENTITY");
    }

    public void sembrarBase() {
        sembrar("2026-11-10T15:00:00Z", "2", "RESERVA", "Reserva", "55", "EXITO", "CREAR", "c-1");
        sembrar("2026-11-11T15:00:00Z", "2", "RESERVA", "Reserva", "55", "EXITO", "PAGAR", "c-2");
        sembrar("2026-11-12T15:00:00Z", "2", "CANCELACION", "Reserva", "55", "EXITO", "CANCELAR", "c-3");
        sembrar("2026-11-13T15:00:00Z", "2", "CANCELACION", "Reserva", "55", "EXITO", "DEVOLVER", "c-3");
        sembrar("2026-11-14T15:00:00Z", "1", "PUBLICACION", "Publicacion", "10", "EXITO", "CREAR", "c-4");
        sembrar("2026-11-15T15:00:00Z", null, "AUTENTICACION", "Usuario", null, "FALLO", "LOGIN", null);
        sembrar("2026-11-16T15:00:00Z", "2", "RESERVA", "Reserva", "77", "FALLO", "CREAR", "c-5");
    }

    public void sembrar(String fecha, String usuario, String accion, String entidad, String entidadId,
            String resultado, String operacion, String correlacion) {
        jdbc.update(INSERTAR, fecha, usuario, accion, entidad, entidadId, resultado, operacion, correlacion);
    }

    public int contar() {
        return jdbc.queryForObject("SELECT count(*) FROM evento_auditoria", Integer.class);
    }
}
