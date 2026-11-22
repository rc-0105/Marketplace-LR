package co.edu.unbosque.marketplace.indicadores.repository;

import co.edu.unbosque.marketplace.indicadores.services.CiudadDemanda;
import co.edu.unbosque.marketplace.indicadores.services.RangoFechas;
import co.edu.unbosque.marketplace.indicadores.services.TipoReporte;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas agregadas de solo lectura sobre usuario, publicacion y reserva (RF-41). Lee las tablas
 * directamente para no acoplar este módulo a los servicios de los demás. Cada indicador filtra por
 * el {@code creado_en} de su propia tabla; los filtros de fecha son fragmentos fijos y los valores
 * van siempre como parámetros.
 */
@Repository
public class IndicadoresRepository {

    /** Una PENDIENTE cuyo apartado ya se agotó se cuenta como VENCIDA, igual que en Reserva.estadoActual. */
    private static final String ESTADO_EFECTIVO =
            "CASE WHEN r.estado = 'PENDIENTE' AND r.vence_en <= :ahora THEN 'VENCIDA' ELSE r.estado END";

    /** Reservas que suman a los ingresos estimados; ver {@link #ingresosEstimados}. */
    private static final String ESTADOS_PAGADOS = "('CONFIRMADA', 'COMPLETADA', 'EN_REVISION_MANUAL')";

    /** Las fechas de creación de los reportes, como texto en hora de Bogotá (RF-42). */
    private static final String FORMATO_FECHA_HORA = "'YYYY-MM-DD HH24:MI'";

    private final NamedParameterJdbcTemplate jdbc;

    public IndicadoresRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cuentas no eliminadas creadas en el rango, por rol; los roles sin cuentas no aparecen. */
    public Map<String, Long> usuariosPorRol(RangoFechas rango) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String sql = "SELECT u.rol, COUNT(*) AS total FROM usuario u WHERE u.estado <> 'ELIMINADA'"
                + filtroFecha("u.creado_en", rango, parametros)
                + " GROUP BY u.rol";
        return conteos(sql, parametros, "rol");
    }

    /** Publicaciones ACTIVA creadas en el rango. */
    public long publicacionesActivas(RangoFechas rango) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM publicacion p WHERE p.estado = 'ACTIVA'"
                + filtroFecha("p.creado_en", rango, parametros);
        Long total = jdbc.queryForObject(sql, parametros, Long.class);
        return total == null ? 0 : total;
    }

    /** Reservas creadas en el rango, por estado efectivo; los estados sin reservas no aparecen. */
    public Map<String, Long> reservasPorEstado(RangoFechas rango, Instant ahora) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("ahora", Timestamp.from(ahora));
        String sql = "SELECT " + ESTADO_EFECTIVO + " AS estado, COUNT(*) AS total FROM reserva r WHERE 1 = 1"
                + filtroFecha("r.creado_en", rango, parametros)
                + " GROUP BY 1";
        return conteos(sql, parametros, "estado");
    }

    /**
     * Suma del total de las reservas pagadas creadas en el rango: CONFIRMADA, COMPLETADA y
     * EN_REVISION_MANUAL (una confirmada cuya publicación se bloqueó; si el administrador la cancela,
     * pasa a CANCELADA y deja de sumar).
     */
    public BigDecimal ingresosEstimados(RangoFechas rango) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String sql = "SELECT COALESCE(SUM(r.total), 0) FROM reserva r"
                + " WHERE r.estado IN " + ESTADOS_PAGADOS
                + filtroFecha("r.creado_en", rango, parametros);
        BigDecimal total = jdbc.queryForObject(sql, parametros, BigDecimal.class);
        return total == null ? BigDecimal.ZERO : total;
    }

    /**
     * Las ciudades cuyas publicaciones recibieron más reservas creadas en el rango, en cualquier
     * estado; empates por nombre. Las publicaciones sin ciudad no cuentan.
     */
    public List<CiudadDemanda> ciudadesMayorDemanda(RangoFechas rango, int limite) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("limite", limite);
        String sql = "SELECT TRIM(p.ciudad) AS ciudad, COUNT(*) AS reservas"
                + " FROM reserva r JOIN publicacion p ON p.id = r.publicacion_id"
                + " WHERE p.ciudad IS NOT NULL AND TRIM(p.ciudad) <> ''"
                + filtroFecha("r.creado_en", rango, parametros)
                + " GROUP BY TRIM(p.ciudad) ORDER BY reservas DESC, ciudad ASC LIMIT :limite";
        return jdbc.query(sql, parametros,
                (fila, numero) -> new CiudadDemanda(fila.getString("ciudad"), fila.getLong("reservas")));
    }

    /**
     * Las filas de un reporte (RF-43), con una celda por encabezado de {@link TipoReporte} y en su
     * orden. Usa los mismos filtros que los indicadores: cada reporte filtra por el {@code creado_en}
     * de su tabla principal, usuarios excluye las cuentas eliminadas, reservas usa el estado efectivo
     * e ingresos suma los mismos estados pagados.
     */
    public List<List<Object>> filasReporte(TipoReporte tipo, RangoFechas rango, Instant ahora) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("ahora", Timestamp.from(ahora));
        String sql = switch (tipo) {
            case USUARIOS -> "SELECT u.id, u.nombre, u.correo, u.rol, u.estado, " + fechaHora("u.creado_en")
                    + " FROM usuario u WHERE u.estado <> 'ELIMINADA'"
                    + filtroFecha("u.creado_en", rango, parametros)
                    + " ORDER BY u.creado_en, u.id";
            case PUBLICACIONES -> "SELECT p.id, p.titulo, NULLIF(TRIM(p.ciudad), ''), t.nombre, p.estado,"
                    + " p.precio_noche, a.correo, " + fechaHora("p.creado_en")
                    + " FROM publicacion p JOIN usuario a ON a.id = p.anfitrion_id"
                    + " LEFT JOIN tipo_alojamiento t ON t.id = p.tipo_id WHERE 1 = 1"
                    + filtroFecha("p.creado_en", rango, parametros)
                    + " ORDER BY p.creado_en, p.id";
            case RESERVAS -> "SELECT r.id, p.titulo, NULLIF(TRIM(p.ciudad), ''), h.correo,"
                    + " to_char(r.llegada, 'YYYY-MM-DD'), to_char(r.salida, 'YYYY-MM-DD'), r.noches, "
                    + ESTADO_EFECTIVO + ", r.total, " + fechaHora("r.creado_en")
                    + " FROM reserva r JOIN publicacion p ON p.id = r.publicacion_id"
                    + " JOIN usuario h ON h.id = r.huesped_id WHERE 1 = 1"
                    + filtroFecha("r.creado_en", rango, parametros)
                    + " ORDER BY r.creado_en, r.id";
            case INGRESOS -> "SELECT to_char(r.creado_en AT TIME ZONE 'America/Bogota', 'YYYY-MM') AS mes,"
                    + " COUNT(*), SUM(r.total) FROM reserva r WHERE r.estado IN " + ESTADOS_PAGADOS
                    + filtroFecha("r.creado_en", rango, parametros)
                    + " GROUP BY 1 ORDER BY 1";
            case DEMANDA -> "SELECT COALESCE(NULLIF(TRIM(p.ciudad), ''), 'Sin ciudad') AS ciudad,"
                    + " COALESCE(t.nombre, 'Sin tipo') AS tipo, COUNT(*) AS reservas"
                    + " FROM reserva r JOIN publicacion p ON p.id = r.publicacion_id"
                    + " LEFT JOIN tipo_alojamiento t ON t.id = p.tipo_id WHERE 1 = 1"
                    + filtroFecha("r.creado_en", rango, parametros)
                    + " GROUP BY 1, 2 ORDER BY reservas DESC, ciudad ASC, tipo ASC";
        };
        int columnas = tipo.encabezados().size();
        return jdbc.query(sql, parametros, (fila, numero) -> {
            List<Object> celdas = new ArrayList<>(columnas);
            for (int columna = 1; columna <= columnas; columna++) {
                celdas.add(fila.getObject(columna));
            }
            return celdas;
        });
    }

    private static String fechaHora(String columna) {
        return "to_char(" + columna + " AT TIME ZONE 'America/Bogota', " + FORMATO_FECHA_HORA + ")";
    }

    private static String filtroFecha(String columna, RangoFechas rango, MapSqlParameterSource parametros) {
        StringBuilder filtro = new StringBuilder();
        if (rango.inicio() != null) {
            filtro.append(" AND ").append(columna).append(" >= :inicio");
            parametros.addValue("inicio", Timestamp.from(rango.inicio()));
        }
        if (rango.fin() != null) {
            filtro.append(" AND ").append(columna).append(" < :fin");
            parametros.addValue("fin", Timestamp.from(rango.fin()));
        }
        return filtro.toString();
    }

    private Map<String, Long> conteos(String sql, MapSqlParameterSource parametros, String columnaClave) {
        Map<String, Long> conteos = new HashMap<>();
        jdbc.query(sql, parametros, (RowCallbackHandler) fila ->
                conteos.put(fila.getString(columnaClave), fila.getLong("total")));
        return conteos;
    }
}
