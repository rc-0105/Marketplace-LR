package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.identidad.repository.SesionInvalidadaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Denylist de sesiones: guarda los tokens cerrados y limpia los que ya no importan (RF-03). */
@Service
public class SesionService {

    /** Debe superar el desfase de reloj del decoder: nunca se borra un token que aún se acepta. */
    public static final Duration MARGEN_LIMPIEZA = Duration.ofMinutes(10);

    private final SesionInvalidadaRepository sesiones;
    private final Clock reloj;

    public SesionService(SesionInvalidadaRepository sesiones, Clock reloj) {
        this.sesiones = sesiones;
        this.reloj = reloj;
    }

    @Transactional
    public void invalidar(String jti, Long usuarioId, Instant expiraEn) {
        sesiones.insertarSiAusente(jti, usuarioId, expiraEn);
    }

    @Transactional(readOnly = true)
    public boolean estaInvalidada(String jti) {
        return sesiones.existsById(jti);
    }

    @Transactional
    public int limpiarVencidas() {
        return sesiones.eliminarVencidasAntesDe(reloj.instant().minus(MARGEN_LIMPIEZA));
    }
}
