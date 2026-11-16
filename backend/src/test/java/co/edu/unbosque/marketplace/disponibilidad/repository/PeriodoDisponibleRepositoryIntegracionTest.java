package co.edu.unbosque.marketplace.disponibilidad.repository;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** La consulta del calendario sobre PostgreSQL: la ventana y los periodos son semiabiertos (REQ-CD10). */
@SpringBootTest
class PeriodoDisponibleRepositoryIntegracionTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 12, 10);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 20);

    @Autowired
    private PeriodoDisponibleRepository periodos;

    @Autowired
    private PublicacionRepository publicaciones;

    @Autowired
    private UsuarioRepository usuarios;

    private Long publicacionId;
    private Long otraPublicacionId;

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        Long anfitrionId = usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", "hash", Rol.ANFITRION))
                .getId();
        publicacionId = publicaciones.save(Publicacion.borrador(anfitrionId, datos("Casa uno"))).getId();
        otraPublicacionId = publicaciones.save(Publicacion.borrador(anfitrionId, datos("Casa dos"))).getId();
    }

    // Los periodos caen en cascada con la publicación; si quedan, otras pruebas no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("Un periodo dentro de la ventana se devuelve y los que solo la tocan por el borde no")
    void excluyeLosPeriodosQueSoloTocanLaVentana() {
        guardar(publicacionId, "2026-12-01", "2026-12-10");
        guardar(publicacionId, "2026-12-12", "2026-12-15");
        guardar(publicacionId, "2026-12-20", "2026-12-25");

        List<PeriodoDisponible> encontrados = consultar(publicacionId);

        assertThat(encontrados).extracting(PeriodoDisponible::getFechaInicio)
                .containsExactly(LocalDate.of(2026, 12, 12));
    }

    @Test
    @DisplayName("Un periodo que cruza el borde de la ventana o la cubre entera se devuelve")
    void incluyeLosPeriodosQueCruzanLosBordes() {
        guardar(publicacionId, "2026-12-05", "2026-12-12");
        guardar(publicacionId, "2026-12-18", "2026-12-30");

        assertThat(consultar(publicacionId)).extracting(PeriodoDisponible::getFechaInicio)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 18));
    }

    @Test
    @DisplayName("Un periodo que cubre toda la ventana se devuelve")
    void incluyeElPeriodoQueCubreLaVentana() {
        guardar(publicacionId, "2026-12-01", "2026-12-31");

        assertThat(consultar(publicacionId)).hasSize(1);
    }

    @Test
    @DisplayName("Un periodo que termina justo al empezar la ventana y otro de otra publicación no se devuelven")
    void soloDevuelveLosPeriodosDeLaPublicacion() {
        guardar(otraPublicacionId, "2026-12-12", "2026-12-15");
        guardar(publicacionId, "2026-12-08", "2026-12-10");

        assertThat(consultar(publicacionId)).isEmpty();
        assertThat(consultar(otraPublicacionId)).hasSize(1);
    }

    private List<PeriodoDisponible> consultar(Long id) {
        return periodos.findByPublicacionIdAndFechaInicioLessThanAndFechaFinGreaterThan(id, HASTA, DESDE);
    }

    private void guardar(Long id, String inicio, String fin) {
        periodos.saveAndFlush(PeriodoDisponible.nuevo(id, LocalDate.parse(inicio), LocalDate.parse(fin)));
    }

    private static DatosBorrador datos(String titulo) {
        return new DatosBorrador(titulo, null, null, null, null, null, null, null, null, null);
    }
}
