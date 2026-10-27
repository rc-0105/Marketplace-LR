package co.edu.unbosque.marketplace.config;

import co.edu.unbosque.marketplace.identidad.services.SesionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/** Rechaza los tokens cuyo jti está en la denylist de sesiones cerradas (RF-03). */
@Component
public class ValidadorSesionVigente implements OAuth2TokenValidator<Jwt> {

    private static final Logger LOG = LoggerFactory.getLogger(ValidadorSesionVigente.class);

    private final SesionService sesiones;

    public ValidadorSesionVigente(SesionService sesiones) {
        this.sesiones = sesiones;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            if (sesiones.estaInvalidada(jwt.getId())) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
            return OAuth2TokenValidatorResult.success();
        } catch (DataAccessException | TransactionException error) {
            // Solo el tipo de la causa: nunca el token ni el mensaje del driver.
            LOG.error("No se pudo consultar la denylist de sesiones: {}", error.getClass().getSimpleName());
            throw new SesionNoVerificableException(error);
        }
    }
}
