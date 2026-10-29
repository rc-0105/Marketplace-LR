package co.edu.unbosque.marketplace.alojamientos.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServicioServiceTest {

    private final ServicioRepository catalogo = mock(ServicioRepository.class);
    private final ServicioService servicio = new ServicioService(catalogo);

    @Test
    @DisplayName("Lista el catálogo completo en orden alfabético")
    void listaElCatalogoEnOrdenAlfabetico() {
        Servicio cocina = new Servicio("Cocina");
        Servicio wifi = new Servicio("Wifi");
        when(catalogo.findAllByOrderByNombreAsc()).thenReturn(List.of(cocina, wifi));

        assertThat(servicio.listar()).containsExactly(cocina, wifi);
    }
}
