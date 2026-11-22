package co.edu.unbosque.marketplace.indicadores.repository;

import co.edu.unbosque.marketplace.indicadores.services.CiudadDemanda;
import co.edu.unbosque.marketplace.indicadores.services.RangoFechas;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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
                + " WHERE r.estado IN ('CONFIRMADA', 'COMPLETADA', 'EN_REVISION_MANUAL')"
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
