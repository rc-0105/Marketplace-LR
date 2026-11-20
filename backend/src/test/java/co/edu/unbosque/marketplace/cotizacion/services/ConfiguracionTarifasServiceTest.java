package co.edu.unbosque.marketplace.cotizacion.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.cotizacion.model.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.repository.ConfiguracionCotizacionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;

class ConfiguracionTarifasServiceTest {

    private static final Long ADMINISTRADOR_ID = 1L;

    private ConfiguracionCotizacionRepository configuraciones;
    private PublicadorEventos publicador;
    private ConfiguracionTarifasService servicio;

    @BeforeEach
    void preparar() {
        configuraciones = mock(ConfiguracionCotizacionRepository.class);
        publicador = mock(PublicadorEventos.class);
        servicio = new ConfiguracionTarifasService(configuraciones, publicador);
        when(configuraciones.findById(ConfiguracionCotizacion.ID))
                .thenReturn(Optional.of(new ConfiguracionCotizacion(new BigDecimal("10"), BigDecimal.ZERO)));
        when(configuraciones.save(any(ConfiguracionCotizacion.class))).thenAnswer(AdditionalAnswers.returnsFirstArg());
    }

    @Test
    @DisplayName("Consulta los porcentajes con que se calcula hoy una cotización")
    void consultaLosPorcentajesVigentes() {
        ConfiguracionCotizacion configuracion = servicio.consultar();

        assertThat(configuracion.getPorcentajeServicio()).isEqualByComparingTo("10");
        assertThat(configuracion.getPorcentajeDescuento()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Actualiza ambos porcentajes y guarda la configuración")
    void actualizaLosDosPorcentajes() {
        ConfiguracionCotizacion guardada = servicio.actualizar(ADMINISTRADOR_ID, new BigDecimal("15.5"),
                new BigDecimal("5"));

        assertThat(guardada.getPorcentajeServicio()).isEqualByComparingTo("15.5");
        assertThat(guardada.getPorcentajeDescuento()).isEqualByComparingTo("5");
        verify(configuraciones).save(guardada);
    }

    @Test
    @DisplayName("El cambio queda como acción administrativa con quién lo hizo y los nuevos valores")
    void auditaElCambio() {
        servicio.actualizar(ADMINISTRADOR_ID, new BigDecimal("15.50"), new BigDecimal("5"));

        ArgumentCaptor<EventoCritico> captor = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(captor.capture());
        EventoCritico evento = captor.getValue();
        assertThat(evento.usuario()).isEqualTo("1");
        assertThat(evento.accion()).isEqualTo(AccionCritica.ACCION_ADMINISTRATIVA);
        assertThat(evento.entidad()).isEqualTo("ConfiguracionCotizacion");
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.datos()).containsEntry("operacion", "configurar_tarifas")
                .containsEntry("porcentajeServicio", "15.50").containsEntry("porcentajeDescuento", "5");
    }

    @Test
    @DisplayName("Si falta la fila de configuración falla y no audita nada")
    void sinConfiguracionFalla() {
        when(configuraciones.findById(ConfiguracionCotizacion.ID)).thenReturn(Optional.empty());

        try {
            servicio.actualizar(ADMINISTRADOR_ID, BigDecimal.TEN, BigDecimal.ZERO);
            fail("Debía fallar porque falta la configuración");
        } catch (IllegalStateException esperado) {
            assertThat(esperado).hasMessage("Falta la configuración de la cotización");
        }
        verify(publicador, never()).publicar(any(EventoCritico.class));
    }
}
