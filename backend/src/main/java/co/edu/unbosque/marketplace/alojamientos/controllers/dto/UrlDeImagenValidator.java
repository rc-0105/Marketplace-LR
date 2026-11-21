package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.AlmacenImagenes;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * Acepta una URL http o https bien formada, o exactamente la ruta pública de una imagen subida
 * ({@code /api/v1/imagenes/} más un UUID). Cualquier otra ruta relativa se rechaza: no se puede apuntar a
 * recursos ajenos de la API.
 */
public class UrlDeImagenValidator implements ConstraintValidator<UrlDeImagen, String> {

    private static final Pattern IMAGEN_SUBIDA = Pattern.compile(
            Pattern.quote(AlmacenImagenes.RUTA_PUBLICA)
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HTTP_O_HTTPS = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        // La ausencia o el vacío los reportan @NotNull y @NotBlank, cada uno con su mensaje.
        if (valor == null || valor.isBlank()) {
            return true;
        }
        if (IMAGEN_SUBIDA.matcher(valor).matches()) {
            return true;
        }
        if (!HTTP_O_HTTPS.matcher(valor).matches()) {
            return false;
        }
        try {
            URI.create(valor).toURL();
            return true;
        } catch (MalformedURLException | IllegalArgumentException error) {
            return false;
        }
    }
}
