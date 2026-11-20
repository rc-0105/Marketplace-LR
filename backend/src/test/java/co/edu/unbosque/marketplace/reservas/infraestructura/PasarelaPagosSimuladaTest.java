package co.edu.unbosque.marketplace.reservas.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.CobroAprobado;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.CobroRechazado;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.ResultadoCobro;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.SolicitudCobro;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pasarela sin red (SOF-34): imita las respuestas de Stripe a sus métodos de prueba. */
class PasarelaPagosSimuladaTest {

    private final PasarelaPagosSimulada pasarela = new PasarelaPagosSimulada();

    @Test
    @DisplayName("Una tarjeta de prueba válida se aprueba y el reintento con la misma clave da la misma transacción")
    void apruebaYRespetaLaIdempotencia() {
        ResultadoCobro primero = pasarela.cobrar(solicitud("pm_card_visa", "reserva-1-pm_card_visa"));
        ResultadoCobro reintento = pasarela.cobrar(solicitud("pm_card_visa", "reserva-1-pm_card_visa"));
        ResultadoCobro otro = pasarela.cobrar(solicitud("pm_card_visa", "reserva-2-pm_card_visa"));

        assertThat(primero).isInstanceOfSatisfying(CobroAprobado.class,
                aprobado -> assertThat(aprobado.transaccionId()).startsWith("sim_"));
        assertThat(reintento).isEqualTo(primero);
        assertThat(otro).isNotEqualTo(primero);
    }

    @Test
    @DisplayName("Las tarjetas de prueba de rechazo se rechazan con el motivo que daría Stripe")
    void rechazaLasTarjetasDeRechazo() {
        assertThat(pasarela.cobrar(solicitud("pm_card_chargeDeclined", "a")))
                .isEqualTo(new CobroRechazado("card_declined"));
        assertThat(pasarela.cobrar(solicitud("pm_card_chargeDeclinedInsufficientFunds", "b")))
                .isEqualTo(new CobroRechazado("insufficient_funds"));
    }

    @Test
    @DisplayName("La conciliación encuentra el cobro aprobado de la reserva, y nada si se rechazó o no se cobró")
    void encuentraElCobroAprobadoDeLaReserva() {
        ResultadoCobro aprobado = pasarela.cobrar(new SolicitudCobro(5L, BigDecimal.TEN, "pm_card_visa", "reserva-5"));
        pasarela.cobrar(new SolicitudCobro(6L, BigDecimal.TEN, "pm_card_chargeDeclined", "reserva-6"));

        assertThat(pasarela.buscarCobroAprobado("reserva-5")).contains(((CobroAprobado) aprobado).transaccionId());
        assertThat(pasarela.buscarCobroAprobado("reserva-6")).isEmpty();
        assertThat(pasarela.buscarCobroAprobado("reserva-7")).isEmpty();
    }

    private static SolicitudCobro solicitud(String metodo, String clave) {
        return new SolicitudCobro(1L, new BigDecimal("100000"), metodo, clave);
    }
}
