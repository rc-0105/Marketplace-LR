package co.edu.unbosque.marketplace.auditoria.infraestructura;

import static co.edu.unbosque.marketplace.auditoria.infraestructura.CapturaAuditoria.evento;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.infraestructura.web.FiltroCorrelacion;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Recorre el camino real: puerto → bus de Spring → escucha → log JSON. */
@SpringBootTest
class TrazabilidadIntegracionTest {

    @Autowired
    private PublicadorEventos publicador;

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @Test
    @DisplayName("Los eventos de una misma petición comparten el identificador de correlación")
    void compartenCorrelacion() throws Exception {
        try (var captura = new CapturaAuditoria()) {
            // Es lo que deja FiltroCorrelacion al entrar la petición.
            MDC.put(FiltroCorrelacion.CLAVE_MDC, "corr-123");

            publicador.publicar(evento(AccionCritica.RESERVA, Map.of()));
            publicador.publicar(evento(AccionCritica.CANCELACION, Map.of()));

            assertThat(captura.cantidad()).isEqualTo(2);
            assertThat(captura.json(0).get("correlacion").asText()).isEqualTo("corr-123");
            assertThat(captura.json(1).get("correlacion").asText()).isEqualTo("corr-123");
        }
    }
}
