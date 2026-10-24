package co.edu.unbosque.marketplace.auditoria.infraestructura;

import static co.edu.unbosque.marketplace.auditoria.infraestructura.CapturaAuditoria.evento;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegistroEventosCriticosTest {

    private final RegistroEventosCriticos registro = new RegistroEventosCriticos();
    private final CapturaAuditoria captura = new CapturaAuditoria();

    @AfterEach
    void soltar() {
        captura.close();
    }

    @Test
    @DisplayName("Escribe una sola línea JSON con usuario, acción, entidad, fecha y resultado")
    void escribeUnaLineaJson() throws Exception {
        registro.registrar(evento(AccionCritica.RESERVA, Map.of()));

        assertThat(captura.texto(0).strip()).doesNotContain("\n");
        var linea = captura.json(0);
        assertThat(linea.get("usuario").asText()).isEqualTo("usuario-42");
        assertThat(linea.get("accion").asText()).isEqualTo("RESERVA");
        assertThat(linea.get("entidad").asText()).isEqualTo("Reserva");
        assertThat(linea.get("entidadId").asText()).isEqualTo("R-1001");
        assertThat(linea.get("resultado").asText()).isEqualTo("EXITO");
        assertThat(linea.get("fecha").asText()).isEqualTo("2026-10-22T21:00:00Z");
        assertThat(linea.get("logger_name").asText()).isEqualTo(RegistroEventosCriticos.LOGGER);
    }

    @Test
    @DisplayName("La línea no contiene contraseñas, tokens ni datos de pago")
    void noDejaDatosSensibles() {
        registro.registrar(evento(AccionCritica.AUTENTICACION, Map.of(
                "contrasena", "secreta123",
                "token", "eyJhbGciOi",
                "numeroTarjeta", "4111111111111111")));

        assertThat(captura.texto(0)).doesNotContain("secreta123", "eyJhbGciOi", "4111111111111111");
    }
}
