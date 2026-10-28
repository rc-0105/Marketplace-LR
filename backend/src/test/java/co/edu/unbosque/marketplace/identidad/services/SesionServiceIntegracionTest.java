package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.config.ConfiguracionJwt;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.SesionInvalidada;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.SesionInvalidadaRepository;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Recorre la denylist contra PostgreSQL: inserción idempotente y limpieza (S-A7, S-A16, REQ-A7). */
@SpringBootTest
class SesionServiceIntegracionTest {

    private static final int VUELTAS_CONCURRENCIA = 10;

    @Autowired
    private SesionService sesiones;

    @Autowired
    private SesionInvalidadaRepository repositorio;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    private Long usuarioId;

    @BeforeEach
    void prepararUsuario() {
        usuarios.deleteAll();
        usuarioId = usuarios.save(Usuario.nuevo("Ana", "ana@correo.com", "hash", Rol.HUESPED)).getId();
    }

    @Test
    @DisplayName("Invalidar dos veces el mismo jti deja una sola fila y no falla")
    void invalidarEsIdempotente() {
        Instant expiraEn = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        sesiones.invalidar("jti-repetido", usuarioId, expiraEn);
        sesiones.invalidar("jti-repetido", usuarioId, expiraEn);

        List<SesionInvalidada> filas = repositorio.findAll();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getUsuarioId()).isEqualTo(usuarioId);
        assertThat(filas.get(0).getExpiraEn()).isEqualTo(expiraEn);
    }

    @Test
    @DisplayName("Dos cierres de sesión simultáneos con el mismo jti no producen error")
    void invalidarConcurrenteNoFalla() throws Exception {
        Instant expiraEn = Instant.now().plus(1, ChronoUnit.HOURS);
        ExecutorService hilos = Executors.newFixedThreadPool(2);

        try {
            // La barrera suelta ambos hilos a la vez para forzar la carrera sobre ON CONFLICT;
            // se repite porque una sola vuelta puede no llegar a solaparse.
            for (int vuelta = 0; vuelta < VUELTAS_CONCURRENCIA; vuelta++) {
                String jti = "jti-concurrente-" + vuelta;
                CyclicBarrier largada = new CyclicBarrier(2);
                Callable<Boolean> cierre = () -> {
                    largada.await(5, TimeUnit.SECONDS);
                    sesiones.invalidar(jti, usuarioId, expiraEn);
                    return true;
                };

                Future<Boolean> primero = hilos.submit(cierre);
                Future<Boolean> segundo = hilos.submit(cierre);
                assertThat(primero.get()).isTrue();
                assertThat(segundo.get()).isTrue();
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(repositorio.count()).isEqualTo(VUELTAS_CONCURRENCIA);
    }

    @Test
    @DisplayName("estaInvalidada responde true solo para los jti guardados de una cuenta activa")
    void estaInvalidadaDistingueJti() {
        sesiones.invalidar("jti-cerrado", usuarioId, Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(sesiones.estaInvalidada("jti-cerrado", usuarioId)).isTrue();
        assertThat(sesiones.estaInvalidada(UUID.randomUUID().toString(), usuarioId)).isFalse();
    }

    @Test
    @DisplayName("Un usuario que no existe tiene la sesión invalidada aunque el jti no esté en la denylist")
    void usuarioInexistenteEstaInvalidado() {
        assertThat(sesiones.estaInvalidada(UUID.randomUUID().toString(), usuarioId + 1_000L)).isTrue();
    }

    @Test
    @DisplayName("Una cuenta restringida o desactivada tiene la sesión invalidada y al reactivarla vuelve a valer")
    void cuentaNoActivaEstaInvalidada() {
        String jti = UUID.randomUUID().toString();

        jdbc.update("UPDATE usuario SET estado = 'RESTRINGIDA', motivo_restriccion = 'Pagos' WHERE id = ?", usuarioId);
        assertThat(sesiones.estaInvalidada(jti, usuarioId)).isTrue();

        jdbc.update("UPDATE usuario SET estado = 'DESACTIVADA', motivo_restriccion = NULL WHERE id = ?", usuarioId);
        assertThat(sesiones.estaInvalidada(jti, usuarioId)).isTrue();

        jdbc.update("UPDATE usuario SET estado = 'ACTIVA' WHERE id = ?", usuarioId);
        assertThat(sesiones.estaInvalidada(jti, usuarioId)).isFalse();
    }

    @Test
    @DisplayName("La limpieza borra solo las filas vencidas más allá del margen")
    void limpiezaRespetaElMargen() {
        Instant ahora = Instant.now();
        sesiones.invalidar("jti-viejo", usuarioId, ahora.minus(SesionService.MARGEN_LIMPIEZA).minusSeconds(60));
        sesiones.invalidar("jti-en-margen", usuarioId, ahora.minus(SesionService.MARGEN_LIMPIEZA).plusSeconds(60));
        sesiones.invalidar("jti-vigente", usuarioId, ahora.plus(1, ChronoUnit.HOURS));

        int borradas = sesiones.limpiarVencidas();

        assertThat(borradas).isEqualTo(1);
        assertThat(sesiones.estaInvalidada("jti-viejo", usuarioId)).isFalse();
        assertThat(sesiones.estaInvalidada("jti-en-margen", usuarioId)).isTrue();
        assertThat(sesiones.estaInvalidada("jti-vigente", usuarioId)).isTrue();
    }

    @Test
    @DisplayName("El margen de limpieza es mayor que el desfase de reloj aceptado")
    void margenSuperaElDesfase() {
        assertThat(SesionService.MARGEN_LIMPIEZA).isGreaterThan(ConfiguracionJwt.DESFASE_RELOJ);
    }
}
