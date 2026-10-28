package co.edu.unbosque.marketplace.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declara el Bearer JWT en el contrato OpenAPI para que Swagger UI ofrezca el
 * botón Authorize. Solo documenta: quién entra lo sigue decidiendo
 * {@link ConfiguracionSeguridad}, y registro y login ignoran el token.
 */
@Configuration
@OpenAPIDefinition(security = @SecurityRequirement(name = ConfiguracionOpenApi.ESQUEMA_BEARER))
@SecurityScheme(
        name = ConfiguracionOpenApi.ESQUEMA_BEARER,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class ConfiguracionOpenApi {

    static final String ESQUEMA_BEARER = "bearerAuth";
}
