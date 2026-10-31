package co.edu.unbosque.marketplace.alojamientos.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PublicacionTest {

    private static final Long ANFITRION_ID = 7L;

    @Test
    @DisplayName("Un borrador completo pasa a PENDIENTE_REVISION")
    void borradorCompletoPasaARevision() {
        Publicacion publicacion = completa();

        publicacion.enviarARevision();

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.PENDIENTE_REVISION);
    }

    @Test
    @DisplayName("Un borrador incompleto no se envía a revisión y sigue en BORRADOR")
    void borradorIncompletoNoPasaARevision() {
        Publicacion publicacion = Publicacion.borrador(ANFITRION_ID, new DatosBorrador("Casa en la playa", null,
                null, null, null, null, null, Set.of(), null, List.of()));

        assertThatThrownBy(publicacion::enviarARevision).isInstanceOf(IllegalStateException.class);
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BORRADOR);
    }

    @Test
    @DisplayName("Solo un borrador se envía a revisión")
    void soloUnBorradorPasaARevision() {
        Publicacion publicacion = en(EstadoPublicacion.PAUSADA);

        assertThatThrownBy(publicacion::enviarARevision).isInstanceOf(IllegalStateException.class);
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.PAUSADA);
    }

    @Test
    @DisplayName("Una publicación activa se pausa y al reactivarla vuelve a ACTIVA")
    void pausarYReactivar() {
        Publicacion publicacion = en(EstadoPublicacion.ACTIVA);

        publicacion.pausar();
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.PAUSADA);

        publicacion.reactivar();
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.ACTIVA);
    }

    @Test
    @DisplayName("Un borrador no se pausa ni se reactiva")
    void borradorNoSePausaNiSeReactiva() {
        Publicacion publicacion = completa();

        assertThatThrownBy(publicacion::pausar).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(publicacion::reactivar).isInstanceOf(IllegalStateException.class);
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BORRADOR);
    }

    private static Publicacion completa() {
        return Publicacion.borrador(ANFITRION_ID, new DatosBorrador("Casa en la playa", "Frente al mar",
                "Santa Marta", "Calle 1 # 2-3", new TipoAlojamiento("Casa"), 4, new BigDecimal("250000"),
                Set.of(new Servicio("Wifi")), "No se admiten fiestas", List.of("https://imagenes.com/1.jpg")));
    }

    // ACTIVA solo se alcanza con la aprobación del administrador (SOF-23); aquí se fija directo.
    private static Publicacion en(EstadoPublicacion estado) {
        Publicacion publicacion = completa();
        ReflectionTestUtils.setField(publicacion, "estado", estado);
        return publicacion;
    }
}
