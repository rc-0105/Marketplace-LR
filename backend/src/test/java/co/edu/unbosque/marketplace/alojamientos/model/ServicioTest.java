package co.edu.unbosque.marketplace.alojamientos.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServicioTest {

    @Test
    @DisplayName("Dos instancias del mismo servicio son iguales aunque vengan de lugares distintos")
    void mismoNombreEsMismoServicio() {
        Servicio guardado = new Servicio("Wifi");
        Servicio pedido = new Servicio("Wifi");

        assertThat(guardado).isEqualTo(pedido).hasSameHashCodeAs(pedido);
    }

    @Test
    @DisplayName("Servicios con nombres distintos no son iguales")
    void nombresDistintosNoSonIguales() {
        assertThat(new Servicio("Wifi")).isNotEqualTo(new Servicio("Piscina"));
    }

    @Test
    @DisplayName("retainAll conserva el servicio guardado cuando llega otra instancia con el mismo nombre")
    void retainAllConservaElServicioGuardado() {
        Servicio guardado = new Servicio("Wifi");
        Set<Servicio> actuales = new HashSet<>(Set.of(guardado, new Servicio("Piscina")));

        actuales.retainAll(Set.of(new Servicio("Wifi")));

        assertThat(actuales).containsExactly(guardado);
        assertThat(actuales.iterator().next()).isSameAs(guardado);
    }
}
