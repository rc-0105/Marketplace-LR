package co.edu.unbosque.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.identidad.services.SesionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.CannotCreateTransactionException;

@ExtendWith(OutputCaptureExtension.class)
class ValidadorSesionVigenteTest {

    private static final String TOKEN = "token-secreto-que-no-debe-salir-en-logs";

    private final SesionService sesiones = mock(SesionService.class);
    private final ValidadorSesionVigente validador = new ValidadorSesionVigente(sesiones);
    private final Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "HS256").claim("jti", "jti-1").subject("7")
            .build();

    @Test
    @DisplayName("Un jti invalidado falla la validación")
    void rechazaJtiInvalidado() {
        when(sesiones.estaInvalidada("jti-1", 7L)).thenReturn(true);

        assertThat(validador.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("La consulta usa el id de la cuenta que viene en el sub")
    void consultaConElIdDelSubject() {
        validador.validate(jwt);

        verify(sesiones).estaInvalidada("jti-1", 7L);
    }

    @Test
    @DisplayName("Un jti no invalidado pasa la validación")
    void aceptaJtiVigente() {
        when(sesiones.estaInvalidada("jti-1", 7L)).thenReturn(false);

        assertThat(validador.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Un fallo de acceso a datos lanza SesionNoVerificableException y no registra el token")
    void fallaDeDatos(CapturedOutput salida) {
        when(sesiones.estaInvalidada("jti-1", 7L)).thenThrow(new DataAccessResourceFailureException("sin conexión"));

        assertThatThrownBy(() -> validador.validate(jwt)).isInstanceOf(SesionNoVerificableException.class);
        assertThat(salida.getAll()).doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("Un fallo de transacción (conexión caída) también lanza SesionNoVerificableException")
    void fallaDeTransaccion() {
        when(sesiones.estaInvalidada("jti-1", 7L)).thenThrow(new CannotCreateTransactionException("sin conexión"));

        assertThatThrownBy(() -> validador.validate(jwt)).isInstanceOf(SesionNoVerificableException.class);
    }

    @Test
    @DisplayName("Un fallo inesperado se propaga tal cual (no es 503), se registra solo su clase y no el token ni el jti")
    void fallaInesperada(CapturedOutput salida) {
        IllegalStateException inesperada = new IllegalStateException("detalle-del-driver jti-1");
        when(sesiones.estaInvalidada("jti-1", 7L)).thenThrow(inesperada);

        assertThatThrownBy(() -> validador.validate(jwt)).isSameAs(inesperada);
        assertThat(salida.getAll()).contains("IllegalStateException")
                .doesNotContain(TOKEN, "jti-1", "detalle-del-driver");
    }
}
