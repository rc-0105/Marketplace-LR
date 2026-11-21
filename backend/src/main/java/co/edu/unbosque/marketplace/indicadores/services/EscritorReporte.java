package co.edu.unbosque.marketplace.indicadores.services;

/** Escribe un reporte en un formato de archivo (RF-43). Hay uno por {@link FormatoExportacion}. */
public interface EscritorReporte {

    FormatoExportacion formato();

    /** El archivo completo; un reporte sin filas produce solo los encabezados. */
    byte[] escribir(Reporte reporte);
}
