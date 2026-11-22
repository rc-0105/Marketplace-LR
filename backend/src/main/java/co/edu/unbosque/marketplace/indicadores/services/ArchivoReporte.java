package co.edu.unbosque.marketplace.indicadores.services;

/** Un reporte ya escrito en un formato, listo para descargar (RF-43). */
public record ArchivoReporte(String nombre, FormatoExportacion formato, byte[] contenido) {
}
