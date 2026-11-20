package co.edu.unbosque.marketplace.auditoria.services;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AuditoriaServiceTest {

    private final RepositorioEventosFalso repositorio = new RepositorioEventosFalso();
    private final AuditoriaService servicio = new AuditoriaService(repositorio);

    @Test
    @DisplayName("registrar guarda el evento en el repositorio")
    void registrarGuarda() {
        EventoAuditoria fila = EventoAuditoria.de(new EventoCritico("2", AccionCritica.RESERVA, "Reserva", "55",
                ResultadoEvento.EXITO, Instant.parse("2026-11-16T15:00:00Z"), Map.of()), Map.of(), null);

        servicio.registrar(fila);

        assertThat(repositorio.guardados).containsExactly(fila);
    }

    @Test
    @DisplayName("registrar corre en una transacción nueva para sobrevivir al rollback del negocio")
    void registrarEsRequiresNew() throws NoSuchMethodException {
        Method registrar = AuditoriaService.class.getMethod("registrar", EventoAuditoria.class);

        assertThat(registrar.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
