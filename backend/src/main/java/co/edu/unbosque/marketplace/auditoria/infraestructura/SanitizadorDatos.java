package co.edu.unbosque.marketplace.auditoria.infraestructura;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Oculta contraseñas, tokens y datos de pago antes de que lleguen al log. */
final class SanitizadorDatos {

    static final String OCULTO = "***";

    private static final Pattern CLAVES_SENSIBLES = Pattern.compile(
            "contrasena|contraseña|password|clave|token|secret|tarjeta|card|cvv|cvc|pago|payment",
            Pattern.CASE_INSENSITIVE);

    private SanitizadorDatos() {
    }

    static Map<String, Object> sanear(Map<?, ?> datos) {
        Map<String, Object> saneados = new LinkedHashMap<>();
        datos.forEach((clave, valor) -> {
            String nombre = String.valueOf(clave);
            saneados.put(nombre, CLAVES_SENSIBLES.matcher(nombre).find() ? OCULTO : sanearAnidado(valor));
        });
        return saneados;
    }

    private static Object sanearAnidado(Object valor) {
        return valor instanceof Map<?, ?> anidado ? sanear(anidado) : valor;
    }
}
