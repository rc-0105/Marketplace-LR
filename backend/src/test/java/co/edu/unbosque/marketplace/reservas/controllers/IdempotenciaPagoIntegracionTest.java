package co.edu.unbosque.marketplace.reservas.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.reservas.services.FallaPasarelaException;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

class IdempotenciaPagoIntegracionTest extends EscenarioPagoReserva {

    @SpyBean
    private PasarelaPagos pasarela;
    private final String clave = UUID.randomUUID().toString();
    private String token;
    private long id;

    @BeforeEach
    void crearReservaPendiente() throws Exception {
        token = iniciarSesion("ana@correo.com");
        id = reservar(token);
    }

    @Test
    void replicaElExitoSinOtroCobro() throws Exception {
        byte[] original = pagar(token, id, "pm_card_visa", clave).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse().getContentAsByteArray();
        pagar(token, id, "pm_card_visa", clave).andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true")).andExpect(content().bytes(original));
        verify(pasarela).cobrar(any());
    }

    @Test
    void permiteReintentarTrasTiempoAgotado() throws Exception {
        doThrow(new FallaPasarelaException(true, "Tiempo agotado", null)).doCallRealMethod()
                .when(pasarela).cobrar(any());
        pagar(token, id, "pm_card_visa", clave).andExpect(status().isGatewayTimeout());
        pagar(token, id, "pm_card_visa", clave).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        verify(pasarela, times(2)).cobrar(any());
    }

    @Test
    void rechazaOtroMetodoConLaMismaClave() throws Exception {
        pagar(token, id, "pm_card_visa", clave).andExpect(status().isOk());
        pagar(token, id, "pm_card_mastercard", clave).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CLAVE_REUTILIZADA"));
        verify(pasarela).cobrar(any());
    }

    @Test
    void exigeClaveAntesDeCobrar() throws Exception {
        pagar(token, id, "pm_card_visa", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CLAVE_INVALIDA"));
        verify(pasarela, never()).cobrar(any());
    }

    @Test
    void noReplicaElRechazoDeLaReservaCancelada() throws Exception {
        pagar(token, id, "pm_card_chargeDeclined", clave).andExpect(status().isPaymentRequired());
        pagar(token, id, "pm_card_chargeDeclined", clave).andExpect(status().isConflict());
        verify(pasarela).cobrar(any());
    }
}
