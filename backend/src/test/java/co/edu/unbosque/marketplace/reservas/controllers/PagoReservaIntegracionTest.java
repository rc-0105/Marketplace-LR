package co.edu.unbosque.marketplace.reservas.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.compartido.idempotencia.ServicioIdempotencia;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pago de una reserva (SOF-34): token → autorización → servicio → pasarela simulada → PostgreSQL. Usa la
 * pasarela por defecto ({@code pagos.modo=simulado}), sin dobles, así comparte el contexto con las demás
 * pruebas de reservas; el tiempo agotado y la falla de la pasarela se prueban en PagoReservaServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class EscenarioPagoReserva {

    private static final String CONTRASENA = "Segura#2026";
    private static final LocalDate LLEGADA = LocalDate.now().plusDays(10);
    private static final LocalDate SALIDA = LLEGADA.plusDays(4);

    @Autowired
    MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PublicacionRepository publicaciones;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder codificador;
    @Autowired
    private ObjectMapper json;

    Long publicacionId;

    @BeforeEach
    void preparar() throws Exception {
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        usuarios.save(Usuario.nuevo("Carla Ruiz", "carla@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        publicacionId = publicacionActiva();
    }

    // Las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
    }

    ResultActions pagar(String token, long id, String metodo, String clave) throws Exception {
        var solicitud = post("/api/v1/reservas/{id}/pago", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoPago(metodo));
        if (clave != null) {
            solicitud.header(ServicioIdempotencia.CABECERA, clave);
        }
        return mvc.perform(solicitud);
    }

    ResultActions pagar(String token, long id, String metodo) throws Exception {
        return pagar(token, id, metodo, UUID.randomUUID().toString());
    }

    static String cuerpoPago(String metodo) {
        return """
                {"metodoPago": "%s"}
                """.formatted(metodo);
    }

    long reservar(String token) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(ServicioIdempotencia.CABECERA, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    String cuerpoReserva() {
        return """
                {"publicacionId": %d, "llegada": "%s", "salida": "%s", "serviciosAdicionalesIds": []}
                """.formatted(publicacionId, LLEGADA, SALIDA);
    }

    Map<String, Object> fila(long id) {
        return jdbc.queryForMap("SELECT estado, estado_pago, transaccion_id FROM reserva WHERE id = ?", id);
    }

    private Long publicacionActiva() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("luis@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Casa en la playa"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        jdbc.update("UPDATE publicacion SET estado = 'ACTIVA', precio_noche = 100000, tarifa_limpieza = 30000 "
                + "WHERE id = ?", id);
        jdbc.update("INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)",
                id, LocalDate.now().minusDays(1), LocalDate.now().plusDays(60));
        return id;
    }

    String iniciarSesion(String correo) throws Exception {
        String cuerpo = """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, CONTRASENA);
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}

class PagoReservaIntegracionTest extends EscenarioPagoReserva {

    @Test
    @DisplayName("SOF-79: pagar una solicitud PENDIENTE_APROBACION responde 409 y no cobra")
    void unaSolicitudPendienteDeAprobacionNoSePaga() throws Exception {
        jdbc.update("UPDATE publicacion SET modo_reserva = 'APROBACION' WHERE id = ?", publicacionId);
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token);

        pagar(token, id, "pm_card_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Esta solicitud espera la aprobación del anfitrión: todavía no se puede pagar."));

        assertThat(fila(id)).containsEntry("estado", "PENDIENTE_APROBACION").containsEntry("estado_pago", "SIN_PAGO");
    }

    @Test
    @DisplayName("AC1: con el pago exitoso la reserva queda CONFIRMADA y se registra el identificador de la transacción")
    void unPagoExitosoConfirmaLaReserva() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token);

        pagar(token, id, "pm_card_visa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"))
                .andExpect(jsonPath("$.transaccionId").value(startsWith("sim_")));

        Map<String, Object> fila = fila(id);
        assertThat(fila).containsEntry("estado", "CONFIRMADA").containsEntry("estado_pago", "APROBADO");
        assertThat((String) fila.get("transaccion_id")).startsWith("sim_");
    }

    @Test
    @DisplayName("AC2: si la pasarela rechaza el pago, la reserva no se confirma y el periodo queda libre para otro")
    void unPagoRechazadoLiberaElPeriodo() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token);

        pagar(token, id, "pm_card_chargeDeclined")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.detail").value(
                        "La pasarela rechazó el pago: la reserva no se confirmó y las fechas quedaron libres."))
                .andExpect(jsonPath("$.motivo").value("card_declined"));

        assertThat(fila(id)).containsEntry("estado", "CANCELADA").containsEntry("estado_pago", "RECHAZADO")
                .containsEntry("transaccion_id", null);
        reservar(iniciarSesion("carla@correo.com"));
    }

    @Test
    @DisplayName("Una reserva ya pagada no se cobra de nuevo: 409 y conserva su transacción")
    void unaReservaPagadaNoSeCobraDeNuevo() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token);
        pagar(token, id, "pm_card_visa").andExpect(status().isOk());
        Object transaccion = fila(id).get("transaccion_id");

        pagar(token, id, "pm_card_mastercard")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta reserva ya está pagada."));

        assertThat(fila(id)).containsEntry("transaccion_id", transaccion);
    }

    @Test
    @DisplayName("Una reserva con el apartado vencido no se cobra: 409 y el pago sigue sin registrar")
    void unApartadoVencidoNoSeCobra() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token);
        jdbc.update("UPDATE reserva SET vence_en = now() - interval '1 minute' WHERE id = ?", id);

        pagar(token, id, "pm_card_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Esta reserva ya no se puede pagar: su apartado venció o fue cancelada."));

        assertThat(fila(id)).containsEntry("estado", "PENDIENTE").containsEntry("estado_pago", "SIN_PAGO");
    }

    @Test
    @DisplayName("Una reserva rechazada no se puede volver a pagar")
    void unaReservaRechazadaNoSePaga() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token);
        pagar(token, id, "pm_card_chargeDeclined").andExpect(status().isPaymentRequired());

        pagar(token, id, "pm_card_visa").andExpect(status().isConflict());

        assertThat(fila(id)).containsEntry("estado", "CANCELADA").containsEntry("estado_pago", "RECHAZADO");
    }

    @Test
    @DisplayName("Solo el huésped dueño paga: otro huésped recibe 403, el anfitrión 403 y sin token 401")
    void soloElDuenoPaga() throws Exception {
        long id = reservar(iniciarSesion("ana@correo.com"));

        pagar(iniciarSesion("carla@correo.com"), id, "pm_card_visa").andExpect(status().isForbidden());
        pagar(iniciarSesion("luis@correo.com"), id, "pm_card_visa").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reservas/{id}/pago", id).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPago("pm_card_visa")))
                .andExpect(status().isUnauthorized());

        assertThat(fila(id)).containsEntry("estado", "PENDIENTE").containsEntry("estado_pago", "SIN_PAGO");
    }

    @Test
    @DisplayName("Una reserva que no existe responde 404")
    void unaReservaInexistenteResponde404() throws Exception {
        pagar(iniciarSesion("ana@correo.com"), 999_999L, "pm_card_visa")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No encontramos esa reserva."));
    }

    @Test
    @DisplayName("Solo se aceptan tarjetas de prueba de Stripe: otro método responde 400 y no cobra")
    void soloAceptaTarjetasDePrueba() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token);

        pagar(token, id, "4242424242424242")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.metodoPago").value("Usa una tarjeta de prueba: pm_card_visa, "
                        + "pm_card_mastercard, pm_card_chargeDeclined o pm_card_chargeDeclinedInsufficientFunds"));
        mvc.perform(post("/api/v1/reservas/{id}/pago", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(ServicioIdempotencia.CABECERA, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.metodoPago").value("Indica el método de pago de prueba"));

        assertThat(fila(id)).containsEntry("estado", "PENDIENTE").containsEntry("estado_pago", "SIN_PAGO");
    }

    @Test
    @DisplayName("Una reserva recién creada informa que todavía no tiene pago")
    void unaReservaNuevaInformaQueNoTienePago() throws Exception {
        mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com"))
                        .header(ServicioIdempotencia.CABECERA, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.estadoPago").value("SIN_PAGO"))
                .andExpect(jsonPath("$.transaccionId").doesNotExist());
    }

}
