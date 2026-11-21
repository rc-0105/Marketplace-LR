package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.identidad.services.DirectorioUsuariosService;
import co.edu.unbosque.marketplace.identidad.services.HuespedBasico;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reservas de las publicaciones del anfitrión (SOF-36), en cualquier estado: cierra el hueco donde las
 * reservas solo se podían ver desde el lado del huésped ({@code /reservas/mias}, SOF-35). A diferencia
 * de {@link HistorialReservasService}, no separa futuras de pasadas (D3) y resuelve sus publicaciones
 * antes de consultar, en vez de filtrar por un huésped único.
 */
@Service
public class ReservasAnfitrionService {

    private final ReservaRepository reservas;
    private final PublicacionService publicaciones;
    private final DirectorioUsuariosService usuarios;
    private final Clock reloj;

    public ReservasAnfitrionService(ReservaRepository reservas, PublicacionService publicaciones,
            DirectorioUsuariosService usuarios, Clock reloj) {
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /**
     * Con {@code publicacionId}: solo esa publicación, exigiendo que sea del anfitrión (404 si no existe,
     * 403 si es de otro). Sin él: todas las publicaciones del anfitrión. Sin publicaciones, responde la
     * lista vacía sin tocar el repositorio de reservas (D7).
     */
    @Transactional(readOnly = true)
    public List<ReservaDeAnfitrion> delAnfitrion(Long anfitrionId, Long publicacionId) {
        Set<Long> publicacionIds = resolverPublicacionIds(anfitrionId, publicacionId);
        if (publicacionIds.isEmpty()) {
            return List.of();
        }

        List<Reserva> propias = reservas.findByPublicacionIdInOrderByLlegadaAscIdAsc(publicacionIds);
        Map<Long, AlojamientoReservado> alojamientos = publicaciones.consultarReservados(publicacionIds);
        Map<Long, HuespedBasico> huespedes = usuarios
                .consultarBasicos(propias.stream().map(Reserva::getHuespedId).collect(Collectors.toSet()));

        Instant ahora = Instant.now(reloj);
        return propias.stream()
                .map(reserva -> new ReservaDeAnfitrion(reserva, reserva.estadoActual(ahora),
                        alojamientos.get(reserva.getPublicacionId()), huespedes.get(reserva.getHuespedId())))
                .toList();
    }

    private Set<Long> resolverPublicacionIds(Long anfitrionId, Long publicacionId) {
        if (publicacionId != null) {
            publicaciones.exigirPropietario(publicacionId, anfitrionId);
            return Set.of(publicacionId);
        }
        return publicaciones.listarDelAnfitrion(anfitrionId).stream().map(Publicacion::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
