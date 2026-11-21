package co.edu.unbosque.marketplace.reservas.infraestructura;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Simulación local, independiente del modo de la pasarela de cobros. */
@Component
public class ReembolsosSimulados {
    public String solicitar(Long reservaId, BigDecimal importe) {
        if (importe.signum() <= 0) throw new IllegalArgumentException("El importe debe ser positivo");
        return "sim-" + reservaId;
    }
}
