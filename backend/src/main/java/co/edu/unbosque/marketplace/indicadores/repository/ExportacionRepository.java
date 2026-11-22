package co.edu.unbosque.marketplace.indicadores.repository;

import co.edu.unbosque.marketplace.indicadores.services.FormatoExportacion;
import co.edu.unbosque.marketplace.indicadores.services.RangoFechas;
import co.edu.unbosque.marketplace.indicadores.services.TipoReporte;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Registro de auditoría de las exportaciones de reportes (RF-44); solo inserta. */
@Repository
public class ExportacionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ExportacionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Guarda quién exportó, cuándo, qué reporte, en qué formato y con qué filtros. El correo sale de
     * la cuenta en ese momento, para que el registro siga diciendo quién fue aunque la cuenta cambie.
     */
    public void registrar(Long administradorId, TipoReporte tipo, FormatoExportacion formato, RangoFechas rango,
            int filas, Instant fecha) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("administrador", administradorId)
                .addValue("tipo", tipo.name())
                .addValue("formato", formato.name())
                .addValue("desde", rango.desde() == null ? null : Date.valueOf(rango.desde()))
                .addValue("hasta", rango.hasta() == null ? null : Date.valueOf(rango.hasta()))
                .addValue("filas", filas)
                .addValue("fecha", Timestamp.from(fecha));
        int insertadas = jdbc.update("""
                INSERT INTO exportacion (administrador_id, administrador_correo, tipo, formato, desde, hasta, filas,
                    creado_en)
                SELECT u.id, u.correo, :tipo, :formato, :desde, :hasta, :filas, :fecha
                FROM usuario u WHERE u.id = :administrador
                """, parametros);
        if (insertadas != 1) {
            // Sin registro no hay exportación: la transacción se revierte y el archivo no se entrega.
            throw new IllegalStateException("No existe la cuenta " + administradorId + " que pidió la exportación");
        }
    }
}
