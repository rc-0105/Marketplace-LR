package co.edu.unbosque.marketplace.cotizacion.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.services.DetalleAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.alojamientos.services.ServicioAdicionalDetalle;
import co.edu.unbosque.marketplace.cotizacion.exceptions.CotizacionInvalidaException;
import co.edu.unbosque.marketplace.cotizacion.model.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.repository.ConfiguracionCotizacionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CotizacionServiceTest {

    private static final Long PUBLICACION = 7L;
    private static final LocalDate LLEGADA = LocalDate.of(2026, 12, 10);
    private static final LocalDate SALIDA = LocalDate.of(2026, 12, 14);
    private static final ServicioAdicionalDetalle DESAYUNO = new ServicioAdicionalDetalle(1L, "Desayuno",
            new BigDecimal("15000.00"));
    private static final ServicioAdicionalDetalle TRASLADO = new ServicioAdicionalDetalle(2L, "Traslado",
            new BigDecimal("40000.00"));

    private PublicacionService publicaciones;
    private ConfiguracionCotizacionRepository configuraciones;
    private CotizacionService servicio;

    @BeforeEach
    void preparar() {
        publicaciones = mock(PublicacionService.class);
        configuraciones = mock(ConfiguracionCotizacionRepository.class);
        servicio = new CotizacionService(publicaciones, configuraciones);
        configurar("10", "0");
        publicar(new BigDecimal("30000.00"));
    }

    private void configurar(String porcentajeServicio, String porcentajeDescuento) {
        when(configuraciones.findById(ConfiguracionCotizacion.ID)).thenReturn(Optional.of(
                new ConfiguracionCotizacion(new BigDecimal(porcentajeServicio), new BigDecimal(porcentajeDescuento))));
    }

    private void publicar(BigDecimal tarifaLimpieza) {
        DetalleAlojamiento alojamiento = new DetalleAlojamiento(PUBLICACION, "Casa 1", "Frente al mar",
                "Santa Marta", "Casa", 4, new BigDecimal("100000.00"), "No fumar", List.of(), List.of(),
                "Flexible", "Devolución total", tarifaLimpieza, List.of(DESAYUNO, TRASLADO));
        when(publicaciones.consultarActiva(PUBLICACION)).thenReturn(alojamiento);
    }

    @Test
    @DisplayName("Del día 10 al 14 son cuatro noches")
    void cuentaLasNoches() {
        Cotizacion cotizacion = servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, null);

        assertThat(cotizacion.noches()).isEqualTo(4);
    }

    @Test
    @DisplayName("Muestra noches, precio base, limpieza, tarifa de servicio, descuento y total")
    void desgloseCompleto() {
        configurar("10", "5");

        Cotizacion cotizacion = servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, null);

        assertThat(cotizacion.precioNoche()).isEqualByComparingTo("100000");
        assertThat(cotizacion.precioBase()).isEqualByComparingTo("400000");
        assertThat(cotizacion.tarifaLimpieza()).isEqualByComparingTo("30000");
        assertThat(cotizacion.tarifaServicio()).isEqualByComparingTo("40000");
        assertThat(cotizacion.descuento()).isEqualByComparingTo("20000");
        assertThat(cotizacion.serviciosAdicionales()).isEmpty();
        assertThat(cotizacion.totalServiciosAdicionales()).isEqualByComparingTo("0");
        assertThat(cotizacion.total()).isEqualByComparingTo("450000");
    }

    @Test
    @DisplayName("Sin tarifa de limpieza la cotización la cuenta como cero")
    void sinTarifaDeLimpieza() {
        publicar(null);

        Cotizacion cotizacion = servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, null);

        assertThat(cotizacion.tarifaLimpieza()).isEqualByComparingTo("0");
        assertThat(cotizacion.total()).isEqualByComparingTo("440000");
    }

    @Test
    @DisplayName("Un servicio adicional es una línea aparte y el total sube exactamente su costo")
    void agregarUnServicioAdicional() {
        Cotizacion sin = servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, Set.of());

        Cotizacion con = servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, Set.of(1L));

        assertThat(con.serviciosAdicionales()).containsExactly(DESAYUNO);
        assertThat(con.totalServiciosAdicionales()).isEqualByComparingTo("15000");
        assertThat(con.total().subtract(sin.total())).isEqualByComparingTo(DESAYUNO.costo());
    }

    @Test
    @DisplayName("Quitar el servicio adicional devuelve el total anterior")
    void quitarElServicioAdicional() {
        Cotizacion original = servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, Set.of());

        servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, Set.of(1L, 2L));
        Cotizacion sinServicios = servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, Set.of());

        assertThat(sinServicios.total()).isEqualByComparingTo(original.total());
    }

    @Test
    @DisplayName("Varios servicios adicionales suman cada uno su costo")
    void variosServiciosAdicionales() {
        Cotizacion cotizacion = servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, Set.of(1L, 2L));

        assertThat(cotizacion.serviciosAdicionales()).containsExactly(DESAYUNO, TRASLADO);
        assertThat(cotizacion.totalServiciosAdicionales()).isEqualByComparingTo("55000");
        assertThat(cotizacion.total()).isEqualByComparingTo("525000");
    }

    @Test
    @DisplayName("Un servicio adicional que no es de la publicación no se cotiza")
    void servicioAdicionalAjeno() {
        assertThatThrownBy(() -> servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, Set.of(1L, 99L)))
                .isInstanceOf(CotizacionInvalidaException.class);
    }

    @Test
    @DisplayName("Una salida que no es posterior a la llegada no se cotiza ni consulta la publicación")
    void salidaNoPosterior() {
        PublicacionService sinUso = mock(PublicacionService.class);
        CotizacionService otro = new CotizacionService(sinUso, configuraciones);

        assertThatThrownBy(() -> otro.cotizar(PUBLICACION, LLEGADA, LLEGADA, null))
                .isInstanceOf(CotizacionInvalidaException.class);
        assertThatThrownBy(() -> otro.cotizar(PUBLICACION, SALIDA, LLEGADA, null))
                .isInstanceOf(CotizacionInvalidaException.class);
        verifyNoInteractions(sinUso);
    }

    @Test
    @DisplayName("Una publicación inexistente o inactiva responde 404")
    void publicacionNoActiva() {
        when(publicaciones.consultarActiva(PUBLICACION)).thenThrow(new PublicacionNoEncontradaException());

        assertThatThrownBy(() -> servicio.cotizar(PUBLICACION, LLEGADA, SALIDA, null))
                .isInstanceOf(PublicacionNoEncontradaException.class);
    }
}
