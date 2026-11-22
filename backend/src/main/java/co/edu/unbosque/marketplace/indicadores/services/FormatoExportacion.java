package co.edu.unbosque.marketplace.indicadores.services;

import co.edu.unbosque.marketplace.indicadores.exceptions.FormatoExportacionDesconocidoException;
import java.util.Arrays;
import org.springframework.http.MediaType;

/** Formatos de archivo en que se exporta un reporte (RF-43). */
public enum FormatoExportacion {

    PDF("pdf", MediaType.APPLICATION_PDF),
    XLSX("xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final String extension;
    private final MediaType tipoContenido;

    FormatoExportacion(String extension, MediaType tipoContenido) {
        this.extension = extension;
        this.tipoContenido = tipoContenido;
    }

    /** El formato del parámetro {@code formato}; cualquier otro valor responde 400. */
    public static FormatoExportacion desdeParametro(String valor) {
        return Arrays.stream(values())
                .filter(formato -> formato.extension.equals(valor))
                .findFirst()
                .orElseThrow(FormatoExportacionDesconocidoException::new);
    }

    public String extension() {
        return extension;
    }

    public MediaType tipoContenido() {
        return tipoContenido;
    }
}
