package co.edu.unbosque.marketplace.indicadores.controllers.dto;

import co.edu.unbosque.marketplace.indicadores.services.Reporte;
import java.time.LocalDate;
import java.util.List;

/** Un reporte tal como se muestra en pantalla; cada fila trae una celda por encabezado, nula si está vacía. */
public record ReporteResponse(
        String tipo,
        String titulo,
        LocalDate desde,
        LocalDate hasta,
        List<String> encabezados,
        List<List<Object>> filas) {

    public static ReporteResponse desde(Reporte reporte) {
        return new ReporteResponse(
                reporte.tipo().ruta(),
                reporte.tipo().titulo(),
                reporte.rango().desde(),
                reporte.rango().hasta(),
                reporte.encabezados(),
                reporte.filas());
    }
}
