package co.edu.unbosque.marketplace.auditoria.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unbosque.marketplace.auditoria.exceptions.EventoAuditoriaNoEncontradoException;
import co.edu.unbosque.marketplace.auditoria.exceptions.FiltroAuditoriaInvalidoException;
import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
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

    @Test
    @DisplayName("Un rango con desde posterior a hasta se rechaza sin consultar el repositorio")
    void rechazaRangoInvertido() {
        FiltroAuditoria filtro = rango(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 15));

        assertThatThrownBy(() -> servicio.consultar(filtro, 0, 50))
                .isInstanceOf(FiltroAuditoriaInvalidoException.class);

        assertThat(repositorio.consultas).isZero();
    }

    @Test
    @DisplayName("desde igual a hasta es un rango válido de un día")
    void aceptaUnSoloDia() {
        LocalDate dia = LocalDate.of(2026, 11, 16);

        servicio.consultar(rango(dia, dia), 0, 50);

        assertThat(repositorio.consultas).isEqualTo(1);
    }

    @ParameterizedTest(name = "evento={0}")
    @ValueSource(strings = {"PAGO", "reserva"})
    @DisplayName("Un evento que no es el nombre exacto de una AccionCritica se rechaza")
    void rechazaEventoDesconocido(String evento) {
        FiltroAuditoria filtro = new FiltroAuditoria(null, evento, null, null, null, null);

        assertThatThrownBy(() -> servicio.consultar(filtro, 0, 50))
                .isInstanceOf(FiltroAuditoriaInvalidoException.class);

        assertThat(repositorio.consultas).isZero();
    }

    @Test
    @DisplayName("obtener devuelve el evento con sus campos nulos tal cual")
    void obtenerExistente() {
        EventoAuditoria fila = EventoAuditoria.de(new EventoCritico(null, AccionCritica.AUTENTICACION, "Usuario",
                null, ResultadoEvento.FALLO, Instant.parse("2026-11-15T15:00:00Z"), Map.of()), Map.of(), null);
        ReflectionTestUtils.setField(fila, "id", 6L);
        repositorio.save(fila);

        EventoAuditoria obtenido = servicio.obtener(6L);

        assertThat(obtenido.getEntidadId()).isNull();
        assertThat(obtenido.getCorrelacion()).isNull();
    }

    @Test
    @DisplayName("obtener de un id inexistente responde que no se encontró")
    void obtenerInexistente() {
        assertThatThrownBy(() -> servicio.obtener(999999L))
                .isInstanceOf(EventoAuditoriaNoEncontradoException.class);
    }

    private static FiltroAuditoria rango(LocalDate desde, LocalDate hasta) {
        return new FiltroAuditoria(null, null, null, null, desde, hasta);
    }
}
