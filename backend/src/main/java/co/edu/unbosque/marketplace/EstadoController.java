package co.edu.unbosque.marketplace;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de verificación del despliegue. Sirve para comprobar que la aplicación
 * levanta y que el contrato OpenAPI se publica.
 */
@RestController
@RequestMapping("/api/estado")
public class EstadoController {

    @GetMapping
    public Map<String, String> estado() {
        return Map.of(
                "aplicacion", "marketplace-app",
                "estado", "activa");
    }
}
