package co.edu.unbosque.marketplace.alojamientos.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.model.PoliticaCancelacion;
import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.TipoAlojamientoRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogoServiceTest {

    private final ServicioRepository servicios = mock(ServicioRepository.class);
    private final TipoAlojamientoRepository tipos = mock(TipoAlojamientoRepository.class);
    private final PoliticaCancelacionRepository politicas = mock(PoliticaCancelacionRepository.class);
    private final CatalogoService catalogo = new CatalogoService(servicios, tipos, politicas);

    @Test
    @DisplayName("Lista las políticas de cancelación de la más flexible a la más estricta")
    void listaLasPoliticasDeLaMasFlexibleALaMasEstricta() {
        PoliticaCancelacion flexible = new PoliticaCancelacion("Flexible", "Devolución total hasta 1 día antes.", 1, 50);
        PoliticaCancelacion estricta = new PoliticaCancelacion("Estricta", "Devolución total hasta 14 días antes.", 14,
                0);
        when(politicas.findAllByOrderByDiasSinCostoAsc()).thenReturn(List.of(flexible, estricta));

        assertThat(catalogo.listarPoliticasCancelacion()).containsExactly(flexible, estricta);
    }

    @Test
    @DisplayName("Lista el catálogo de servicios completo en orden alfabético")
    void listaLosServiciosEnOrdenAlfabetico() {
        Servicio cocina = new Servicio("Cocina");
        Servicio wifi = new Servicio("Wifi");
        when(servicios.findAllByOrderByNombreAsc()).thenReturn(List.of(cocina, wifi));

        assertThat(catalogo.listarServicios()).containsExactly(cocina, wifi);
    }

    @Test
    @DisplayName("Lista el catálogo de tipos de alojamiento completo en orden alfabético")
    void listaLosTiposEnOrdenAlfabetico() {
        TipoAlojamiento apartamento = new TipoAlojamiento("Apartamento");
        TipoAlojamiento casa = new TipoAlojamiento("Casa");
        when(tipos.findAllByOrderByNombreAsc()).thenReturn(List.of(apartamento, casa));

        assertThat(catalogo.listarTiposAlojamiento()).containsExactly(apartamento, casa);
    }
}
