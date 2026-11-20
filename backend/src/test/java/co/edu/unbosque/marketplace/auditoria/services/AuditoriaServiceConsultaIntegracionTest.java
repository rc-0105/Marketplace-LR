package co.edu.unbosque.marketplace.auditoria.services;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.auditoria.SemillaAuditoria;
import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

/** Filtros, orden y paginación de la consulta sobre PostgreSQL con las filas base de la spec (REQ-CA2 a CA5, CA7). */
@SpringBootTest
class AuditoriaServiceConsultaIntegracionTest {

    private final SemillaAuditoria semilla;
    private final AuditoriaService servicio;

    @Autowired
    AuditoriaServiceConsultaIntegracionTest(AuditoriaService servicio, JdbcTemplate jdbc) {
        this.servicio = servicio;
        this.semilla = new SemillaAuditoria(jdbc);
    }

    @BeforeEach
    void vaciar() {
        semilla.vaciar();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "sin filtros        | - | - | - | - | - | - | A7 A6 A5 A4 A3 A2 A1",
            "usuario            | 2 | - | - | - | - | - | A7 A4 A3 A2 A1",
            "evento             | - | CANCELACION | - | - | - | - | A4 A3",
            "entidad            | - | - | Publicacion | - | - | - | A5",
            "entidadId          | - | - | - | 55 | - | - | A4 A3 A2 A1",
            "desde              | - | - | - | - | 2026-11-14 | - | A7 A6 A5",
            "hasta              | - | - | - | - | - | 2026-11-11 | A2 A1",
            "un solo dia        | - | - | - | - | 2026-11-12 | 2026-11-12 | A3",
            "AND incluido       | 2 | RESERVA | Reserva | 55 | 2026-11-10 | 2026-11-11 | A2 A1",
            "AND fuera de rango | 2 | RESERVA | Reserva | 77 | 2026-11-10 | 2026-11-11 | -",
            "AND que excluye    | 1 | RESERVA | - | - | - | - | -",
            "sin coincidencias  | 999 | - | - | - | - | - | -",
            "entidad minuscula  | - | - | reserva | - | - | - | -",
            "vacios             | '' | '' | '' | '' | - | - | A7 A6 A5 A4 A3 A2 A1",
            "solo la reserva 77 | - | - | Reserva | 77 | - | - | A7"})
    @DisplayName("Cada filtro, solo o combinado, devuelve las filas esperadas de más nueva a más vieja")
    void filtros(String caso, String usuario, String evento, String entidad, String entidadId, LocalDate desde,
            LocalDate hasta, String esperado) {
        sembrarBase();

        Page<EventoAuditoria> pagina = servicio.consultar(
                new FiltroAuditoria(usuario, evento, entidad, entidadId, desde, hasta), 0, 50);

        assertThat(etiquetas(pagina)).isEqualTo(esperado == null ? List.of() : List.of(esperado.split(" ")));
    }

    @Test
    @DisplayName("Ante la misma fecha, la fila con mayor id va primero")
    void desempataPorId() {
        sembrar("2026-11-10T15:00:00Z", "2", "RESERVA", "Reserva", "1", "EXITO", "CREAR", null);
        sembrar("2026-11-10T15:00:00Z", "2", "RESERVA", "Reserva", "2", "EXITO", "PAGAR", null);

        assertThat(etiquetas(servicio.consultar(sinFiltros(), 0, 50))).containsExactly("A2", "A1");
    }

    @Test
    @DisplayName("El usuario se compara por igualdad exacta: 2 no devuelve 22")
    void igualdadExacta() {
        sembrar("2026-11-10T15:00:00Z", "22", "RESERVA", "Reserva", "1", "EXITO", "CREAR", null);
        sembrar("2026-11-11T15:00:00Z", "2", "RESERVA", "Reserva", "2", "EXITO", "CREAR", null);

        assertThat(etiquetas(servicio.consultar(new FiltroAuditoria("2", null, null, null, null, null), 0, 50)))
                .containsExactly("A2");
    }

    @Test
    @DisplayName("Los extremos del día se miden en hora de Bogotá")
    void fronterasDeBogota() {
        sembrar("2026-11-16T04:59:59Z", "2", "RESERVA", "Reserva", "1", "EXITO", "CREAR", null);
        sembrar("2026-11-16T05:00:00Z", "2", "RESERVA", "Reserva", "2", "EXITO", "CREAR", null);
        sembrar("2026-11-17T04:59:59.999Z", "2", "RESERVA", "Reserva", "3", "EXITO", "CREAR", null);
        sembrar("2026-11-17T05:00:00Z", "2", "RESERVA", "Reserva", "4", "EXITO", "CREAR", null);
        LocalDate quince = LocalDate.of(2026, 11, 15);
        LocalDate dieciseis = LocalDate.of(2026, 11, 16);

        assertThat(etiquetas(consultarFechas(dieciseis, null))).containsExactly("A4", "A3", "A2");
        assertThat(etiquetas(consultarFechas(null, quince))).containsExactly("A1");
        assertThat(etiquetas(consultarFechas(null, dieciseis))).containsExactly("A3", "A2", "A1");
        assertThat(etiquetas(consultarFechas(dieciseis, dieciseis))).containsExactly("A3", "A2");
    }

    @Test
    @DisplayName("Ordenada por fecha ascendente la reserva 55 reconstruye su secuencia y A3 y A4 comparten correlación")
    void secuenciaPorReserva() {
        sembrarBase();

        List<EventoAuditoria> filas = new ArrayList<>(servicio.consultar(
                new FiltroAuditoria(null, null, "Reserva", "55", null, null), 0, 50).getContent());
        Collections.reverse(filas);

        assertThat(filas).extracting(f -> f.getDatos().get("operacion"))
                .containsExactly("CREAR", "PAGAR", "CANCELAR", "DEVOLVER");
        assertThat(filas).extracting(EventoAuditoria::getAccion)
                .containsExactly("RESERVA", "RESERVA", "CANCELACION", "CANCELACION");
        assertThat(filas).extracting(EventoAuditoria::getCorrelacion).containsExactly("c-1", "c-2", "c-3", "c-3");
    }

    @Test
    @DisplayName("Con 120 filas el tamaño 50 da 3 páginas y la última trae 20")
    void paginaConTamanoPorDefecto() {
        for (int i = 0; i < 120; i++) {
            sembrar("2026-11-10T15:00:00Z", "2", "RESERVA", "Reserva", "1", "EXITO", "CREAR", null);
        }

        Page<EventoAuditoria> tercera = servicio.consultar(sinFiltros(), 2, 50);

        assertThat(tercera.getTotalElements()).isEqualTo(120);
        assertThat(tercera.getTotalPages()).isEqualTo(3);
        assertThat(tercera.getContent()).hasSize(20);
    }

    @ParameterizedTest(name = "pagina {0} tamano {1}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "1 | 3 | A4 A3 A2 | 7 | 3",
            "2 | 3 | A1 | 7 | 3",
            "5 | 50 | - | 7 | 1"})
    @DisplayName("La página pedida trae su porción y el total cuenta todas las filas")
    void paginaExplicita(int pagina, int tamano, String esperado, long total, int paginas) {
        sembrarBase();

        Page<EventoAuditoria> resultado = servicio.consultar(sinFiltros(), pagina, tamano);

        assertThat(etiquetas(resultado)).isEqualTo(esperado == null ? List.of() : List.of(esperado.split(" ")));
        assertThat(resultado.getTotalElements()).isEqualTo(total);
        assertThat(resultado.getTotalPages()).isEqualTo(paginas);
    }

    @Test
    @DisplayName("Con la tabla vacía el total y las páginas son 0")
    void tablaVacia() {
        Page<EventoAuditoria> resultado = servicio.consultar(sinFiltros(), 0, 50);

        assertThat(resultado.getContent()).isEmpty();
        assertThat(resultado.getTotalElements()).isZero();
        assertThat(resultado.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("El total y las páginas respetan los filtros: entidadId=55 con tamaño 3 son 2 páginas")
    void filtrosConPaginacion() {
        sembrarBase();

        Page<EventoAuditoria> resultado = servicio.consultar(
                new FiltroAuditoria(null, null, null, "55", null, null), 0, 3);

        assertThat(etiquetas(resultado)).containsExactly("A4", "A3", "A2");
        assertThat(resultado.getTotalElements()).isEqualTo(4);
        assertThat(resultado.getTotalPages()).isEqualTo(2);
    }

    private Page<EventoAuditoria> consultarFechas(LocalDate desde, LocalDate hasta) {
        return servicio.consultar(new FiltroAuditoria(null, null, null, null, desde, hasta), 0, 50);
    }

    private static FiltroAuditoria sinFiltros() {
        return new FiltroAuditoria(null, null, null, null, null, null);
    }

    private static List<String> etiquetas(Page<EventoAuditoria> pagina) {
        return pagina.getContent().stream().map(fila -> "A" + fila.getId()).toList();
    }

    private void sembrarBase() {
        semilla.sembrarBase();
    }

    private void sembrar(String fecha, String usuario, String accion, String entidad, String entidadId,
            String resultado, String operacion, String correlacion) {
        semilla.sembrar(fecha, usuario, accion, entidad, entidadId, resultado, operacion, correlacion);
    }
}
