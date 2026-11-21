package co.edu.unbosque.marketplace.indicadores.services;

import java.util.List;

/**
 * Un reporte ya calculado: lo que se ve en pantalla y lo que se escribe en cada archivo (RF-43).
 * Cada fila trae una celda por encabezado; una celda vacía es {@code null}.
 */
public record Reporte(TipoReporte tipo, RangoFechas rango, List<List<Object>> filas) {

    public List<String> encabezados() {
        return tipo.encabezados();
    }

    /** Los filtros en palabras, para el encabezado del PDF. */
    public String descripcionRango() {
        if (rango.desde() != null && rango.hasta() != null) {
            return "Desde " + rango.desde() + " hasta " + rango.hasta();
        }
        if (rango.desde() != null) {
            return "Desde " + rango.desde();
        }
        if (rango.hasta() != null) {
            return "Hasta " + rango.hasta();
        }
        return "Todo el histórico";
    }

    /** Por ejemplo {@code reporte-reservas-2026-11-01-a-2026-11-30.xlsx} o {@code reporte-usuarios-historico.pdf}. */
    public String nombreArchivo(FormatoExportacion formato) {
        String filtro;
        if (rango.desde() != null && rango.hasta() != null) {
            filtro = rango.desde() + "-a-" + rango.hasta();
        } else if (rango.desde() != null) {
            filtro = "desde-" + rango.desde();
        } else if (rango.hasta() != null) {
            filtro = "hasta-" + rango.hasta();
        } else {
            filtro = "historico";
        }
        return "reporte-" + tipo.ruta() + "-" + filtro + "." + formato.extension();
    }
}
