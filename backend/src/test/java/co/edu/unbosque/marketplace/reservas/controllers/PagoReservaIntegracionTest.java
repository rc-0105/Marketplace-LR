package co.edu.unbosque.marketplace.reservas.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import co.edu.unbosque.marketplace.reservas.services.FallaPasarelaException;
import co.edu.unbosque.marketplace.reservas.services.PagoReservaService;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.CobroAprobado;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.CobroRechazado;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.SolicitudCobro;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pago de una reserva (SOF-34): token → autorización → servicio → pasarela → PostgreSQL. La pasarela es un
 * doble para poder provocar el rechazo y el tiempo agotado; los adaptadores se prueban aparte.
 */
// Pool chico: este contexto propio (por el @MockBean) se suma a los demás y PostgreSQL limita las conexiones.
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=3")
@AutoConfigureMockMvc
class PagoReservaIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final LocalDate LLEGADA = LocalDate.now().plusDays(10);
    private static final LocalDate SALIDA = LLEGADA.plusDays(4);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PublicacionRepository publicaciones;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder codificador;
    @Autowired
    private ObjectMapper json;

    @MockBean
    private PasarelaPagos pasarela;

    private Long publicacionId;

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

    @Test
    @DisplayName("AC1: con el pago exitoso la reserva queda CONFIRMADA y se registra el identificador de la transacción")
    void unPagoExitosoConfirmaLaReserva() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);
        when(pasarela.cobrar(any())).thenReturn(new CobroAprobado("pi_123"));

        pagar(token, id, "pm_card_visa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"))
                .andExpect(jsonPath("$.transaccionId").value("pi_123"));

        assertThat(fila(id)).containsEntry("estado", "CONFIRMADA").containsEntry("estado_pago", "APROBADO")
                .containsEntry("transaccion_id", "pi_123");
        ArgumentCaptor<SolicitudCobro> solicitud = ArgumentCaptor.forClass(SolicitudCobro.class);
        verify(pasarela).cobrar(solicitud.capture());
        assertThat(solicitud.getValue().reservaId()).isEqualTo(id);
        assertThat(solicitud.getValue().monto()).isEqualByComparingTo(new BigDecimal("470000"));
        assertThat(solicitud.getValue().metodoPago()).isEqualTo("pm_card_visa");
        assertThat(solicitud.getValue().claveIdempotencia()).isEqualTo("reserva-" + id + "-pm_card_visa");
    }

    @Test
    @DisplayName("AC2: si la pasarela rechaza el pago, la reserva no se confirma y el periodo queda libre para otro")
    void unPagoRechazadoLiberaElPeriodo() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);
        when(pasarela.cobrar(any())).thenReturn(new CobroRechazado("card_declined"));

        pagar(token, id, "pm_card_chargeDeclined")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.detail").value(
                        "La pasarela rechazó el pago: la reserva no se confirmó y las fechas quedaron libres."))
                .andExpect(jsonPath("$.motivo").value("card_declined"));

        assertThat(fila(id)).containsEntry("estado", "CANCELADA").containsEntry("estado_pago", "RECHAZADO")
                .containsEntry("transaccion_id", null);
        mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("carla@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(LLEGADA, SALIDA)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC3: si la pasarela no responde a tiempo, informa el problema, deja la reserva PENDIENTE y registra el error")
    void sinRespuestaDejaLaReservaPendienteYRegistraElError() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);
        when(pasarela.cobrar(any())).thenThrow(new FallaPasarelaException(true, "Stripe no respondió", null));
        Logger logger = (Logger) LoggerFactory.getLogger(PagoReservaService.class);
        ListAppender<ILoggingEvent> eventos = new ListAppender<>();
        eventos.start();
        logger.addAppender(eventos);
        try {
            pagar(token, id, "pm_card_visa")
                    .andExpect(status().isGatewayTimeout())
                    .andExpect(jsonPath("$.detail").value("La pasarela de pagos no respondió a tiempo. Tu reserva "
                            + "sigue apartada hasta que venza el apartado: intenta pagar de nuevo."));
        } finally {
            logger.detachAppender(eventos);
        }

        assertThat(fila(id)).containsEntry("estado", "PENDIENTE").containsEntry("estado_pago", "ERROR")
                .containsEntry("transaccion_id", null);
        assertThat(eventos.list).anySatisfy(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
            assertThat(evento.getFormattedMessage()).contains("reserva " + id);
        });
    }

    @Test
    @DisplayName("Tras un tiempo agotado, el reintento con la pasarela disponible confirma la reserva")
    void elReintentoTrasTiempoAgotadoConfirma() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);
        when(pasarela.cobrar(any())).thenThrow(new FallaPasarelaException(true, "Stripe no respondió", null))
                .thenReturn(new CobroAprobado("pi_reintento"));

        pagar(token, id, "pm_card_visa").andExpect(status().isGatewayTimeout());
        pagar(token, id, "pm_card_visa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.transaccionId").value("pi_reintento"));
    }

    @Test
    @DisplayName("Si la pasarela falla sin ser por tiempo, responde 502 y la reserva sigue PENDIENTE con el pago en ERROR")
    void unaFallaDeLaPasarelaResponde502() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);
        when(pasarela.cobrar(any())).thenThrow(new FallaPasarelaException(false, "Invalid API Key", null));

        pagar(token, id, "pm_card_visa")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("La pasarela de pagos no está disponible en este momento. Tu "
                        + "reserva sigue apartada hasta que venza el apartado: intenta pagar de nuevo."));

        assertThat(fila(id)).containsEntry("estado", "PENDIENTE").containsEntry("estado_pago", "ERROR");
    }

    @Test
    @DisplayName("Si el apartado se perdió mientras la pasarela cobraba, el cobro se reembolsa y responde 409")
    void unCobroSinReservaSeReembolsa() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);
        when(pasarela.cobrar(any())).thenAnswer(invocacion -> {
            // Mientras la pasarela cobra, la tarea programada vence el apartado.
            jdbc.update("UPDATE reserva SET estado = 'VENCIDA' WHERE id = ?", id);
            return new CobroAprobado("pi_tarde");
        });

        pagar(token, id, "pm_card_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "El apartado de la reserva venció mientras se procesaba el pago: el cobro fue reembolsado."));

        verify(pasarela).reembolsar("pi_tarde");
        assertThat(fila(id)).containsEntry("estado", "VENCIDA").containsEntry("estado_pago", "SIN_PAGO")
                .containsEntry("transaccion_id", null);
    }

    @Test
    @DisplayName("Si un intento simultáneo ya confirmó con la misma transacción, responde la reserva sin reembolsar")
    void unIntentoSimultaneoConLaMismaTransaccionNoReembolsa() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);
        when(pasarela.cobrar(any())).thenAnswer(invocacion -> {
            // El otro intento, con la misma clave de idempotencia, recibió la misma transacción y confirmó antes.
            jdbc.update("UPDATE reserva SET estado = 'CONFIRMADA', estado_pago = 'APROBADO', transaccion_id = ? "
                    + "WHERE id = ?", "pi_compartido", id);
            return new CobroAprobado("pi_compartido");
        });

        pagar(token, id, "pm_card_visa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.transaccionId").value("pi_compartido"));

        verify(pasarela, never()).reembolsar(anyString());
    }

    @Test
    @DisplayName("Una reserva ya pagada no se cobra de nuevo: 409 sin llamar a la pasarela")
    void unaReservaPagadaNoSeCobraDeNuevo() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);
        when(pasarela.cobrar(any())).thenReturn(new CobroAprobado("pi_123"));
        pagar(token, id, "pm_card_visa").andExpect(status().isOk());

        pagar(token, id, "pm_card_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta reserva ya está pagada."));

        verify(pasarela).cobrar(any());
    }

    @Test
    @DisplayName("Una reserva con el apartado vencido no se cobra: 409 sin llamar a la pasarela")
    void unApartadoVencidoNoSeCobra() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);
        jdbc.update("UPDATE reserva SET vence_en = now() - interval '1 minute' WHERE id = ?", id);

        pagar(token, id, "pm_card_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Esta reserva ya no se puede pagar: su apartado venció o fue cancelada."));

        verify(pasarela, never()).cobrar(any());
        assertThat(fila(id)).containsEntry("estado_pago", "SIN_PAGO");
    }

    @Test
    @DisplayName("Solo el huésped dueño paga: otro huésped recibe 403, el anfitrión 403 y sin token 401")
    void soloElDuenoPaga() throws Exception {
        long id = reservar(iniciarSesion("ana@correo.com"), LLEGADA, SALIDA);

        pagar(iniciarSesion("carla@correo.com"), id, "pm_card_visa").andExpect(status().isForbidden());
        pagar(iniciarSesion("luis@correo.com"), id, "pm_card_visa").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reservas/{id}/pago", id).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPago("pm_card_visa")))
                .andExpect(status().isUnauthorized());

        verify(pasarela, never()).cobrar(any());
        assertThat(fila(id)).containsEntry("estado", "PENDIENTE").containsEntry("estado_pago", "SIN_PAGO");
    }

    @Test
    @DisplayName("Una reserva que no existe responde 404")
    void unaReservaInexistenteResponde404() throws Exception {
        pagar(iniciarSesion("ana@correo.com"), 999_999L, "pm_card_visa")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No encontramos esa reserva."));

        verify(pasarela, never()).cobrar(any());
    }

    @Test
    @DisplayName("Solo se aceptan tarjetas de prueba de Stripe: otro método responde 400 sin llamar a la pasarela")
    void soloAceptaTarjetasDePrueba() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token, LLEGADA, SALIDA);

        pagar(token, id, "4242424242424242")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.metodoPago").value("Usa una tarjeta de prueba: pm_card_visa, "
                        + "pm_card_mastercard, pm_card_chargeDeclined o pm_card_chargeDeclinedInsufficientFunds"));
        mvc.perform(post("/api/v1/reservas/{id}/pago", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.metodoPago").value("Indica el método de pago de prueba"));

        verify(pasarela, never()).cobrar(any());
        verify(pasarela, never()).reembolsar(anyString());
    }

    @Test
    @DisplayName("Una reserva recién creada informa que todavía no tiene pago")
    void unaReservaNuevaInformaQueNoTienePago() throws Exception {
        mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(LLEGADA, SALIDA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.estadoPago").value("SIN_PAGO"))
                .andExpect(jsonPath("$.transaccionId").doesNotExist());
    }

    private ResultActions pagar(String token, long id, String metodo) throws Exception {
        return mvc.perform(post("/api/v1/reservas/{id}/pago", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoPago(metodo)));
    }

    private static String cuerpoPago(String metodo) {
        return """
                {"metodoPago": "%s"}
                """.formatted(metodo);
    }

    private long reservar(String token, LocalDate llegada, LocalDate salida) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(llegada, salida)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private String cuerpoReserva(LocalDate llegada, LocalDate salida) {
        return """
                {"publicacionId": %d, "llegada": "%s", "salida": "%s", "serviciosAdicionalesIds": []}
                """.formatted(publicacionId, llegada, salida);
    }

    private Map<String, Object> fila(long id) {
        return jdbc.queryForMap("SELECT estado, estado_pago, transaccion_id FROM reserva WHERE id = ?", id);
    }

    // Igual que ReservaIntegracionTest: ACTIVA con precio fijo y un periodo que cubre las fechas de las pruebas.
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

    private String iniciarSesion(String correo) throws Exception {
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
