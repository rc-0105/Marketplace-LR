package co.edu.unbosque.marketplace;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de verificación del despliegue. Sirve para comprobar que la aplicación
 * levanta y que el contrato OpenAPI se publica; la versión es el commit desplegado,
 * que el pipeline compara con el que pidió desplegar (SOF-97).
 */
@RestController
@RequestMapping("/api/estado")
public class EstadoController {

    private final String version;

    public EstadoController(@Value("${aplicacion.version}") String version) {
        this.version = version;
    }

    @GetMapping
    public Map<String, String> estado() {
        return Map.of(
                "aplicacion", "marketplace-app",
                "estado", "activa",
                "version", version);
    }
}
