package co.edu.unbosque.marketplace.indicadores.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** El segmento {@code {tipo}} no nombra ningún reporte (RF-43). */
public class TipoReporteDesconocidoException extends NegocioException {

    public TipoReporteDesconocidoException() {
        super(HttpStatus.BAD_REQUEST,
                "Tipo de reporte desconocido. Use usuarios, publicaciones, reservas, ingresos o demanda.");
    }
}
