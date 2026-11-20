package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.infraestructura.RestriccionesPostgres;
import co.edu.unbosque.marketplace.cotizacion.services.Cotizacion;
import co.edu.unbosque.marketplace.cotizacion.services.CotizacionService;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.EstanciaNoDisponibleException;
import co.edu.unbosque.marketplace.disponibilidad.services.DisponibilidadService;
import co.edu.unbosque.marketplace.reservas.exceptions.FechasReservaInvalidasException;
import co.edu.unbosque.marketplace.reservas.exceptions.ModoReservaNoSoportadoException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaConOtrosServiciosException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaSuperpuestaException;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Creación de una reserva sobre una cotización vigente (SOF-33). Solo cubre la publicación en modo
 * {@code INMEDIATA}: una que exige aprobación del anfitrión se rechaza, ese flujo queda para una historia
 * futura.
 *
 * <p>La reserva nace PENDIENTE y aparta las noches solo durante {@code marketplace.reservas.minutos-apartado}
 * minutos (15 por defecto, ADR-4); el pago, que es aparte, la confirma, y si el apartado se agota sin pago
 * pasa a VENCIDA y libera las noches. El servicio no es transaccional: cada escritura lo es dentro de
 * {@link RegistroReservas}, así una violación de la restricción de exclusión no deja la sesión sucia.
 */
@Service
public class ReservaService {

    private static final String ENTIDAD = "Reserva";
    // "Hoy" es el día en Colombia, donde están los alojamientos, no el del servidor.
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final String RESTRICCION_SIN_CRUCE = "reserva_sin_cruce";

    private final ReservaRepository reservas;
    private final CotizacionService cotizaciones;
    private final PublicacionService publicaciones;
    private final DisponibilidadService disponibilidad;
    private final RegistroReservas registro;
    private final PublicadorEventos publicador;
    private final Clock reloj;
    private final Duration apartado;

    public ReservaService(ReservaRepository reservas, CotizacionService cotizaciones,
            PublicacionService publicaciones, DisponibilidadService disponibilidad, RegistroReservas registro,
            PublicadorEventos publicador, Clock reloj,
            @Value("${marketplace.reservas.minutos-apartado:15}") long minutosApartado) {
        this.reservas = reservas;
        this.cotizaciones = cotizaciones;
        this.publicaciones = publicaciones;
        this.disponibilidad = disponibilidad;
        this.registro = registro;
        this.publicador = publicador;
        this.reloj = reloj;
        this.apartado = Duration.ofMinutes(minutosApartado);
    }

    /**
     * Si ya existe una reserva vigente (CONFIRMADA o PENDIENTE sin vencer) del mismo huésped con las mismas
     * fechas exactas y los mismos servicios adicionales, la devuelve tal cual (reintento idempotente, ADR-4)
     * sin recotizar ni guardar de nuevo; con las mismas fechas pero otros servicios responde 409.
     * Si no, valida que la llegada no sea pasada, que la publicación esté en modo {@code INMEDIATA}
     * ({@code APROBACION} se rechaza), vuelve a cotizar para fijar el precio y revalidar que la cotización
     * sigue vigente (AC4) y que la publicación sigue ACTIVA (AC5), y exige que la estancia quepa en un
     * periodo disponible y no esté ocupada. Crea la reserva PENDIENTE, con el apartado vigente por el tiempo
     * configurado; el cruce con otra reserva que se cuela entre la verificación y la inserción (AC2, AC3) lo
     * decide la restricción de exclusión de PostgreSQL.
     */
    public Reserva crear(Long publicacionId, Long huespedId, LocalDate llegada, LocalDate salida,
            Set<Long> adicionalesElegidos) {
        Instant ahora = Instant.now(reloj);
        if (llegada.isBefore(LocalDate.now(reloj.withZone(ZONA)))) {
            throw new FechasReservaInvalidasException("La fecha de llegada no puede ser anterior a hoy.");
        }
        Set<Long> servicios = adicionalesElegidos == null ? Set.of() : Set.copyOf(adicionalesElegidos);
        Optional<Reserva> existente = buscarIdentica(huespedId, publicacionId, llegada, salida, servicios);
        if (existente.isPresent()) {
            return existente.get();
        }

        // El modo se mira antes de cotizar: una publicación que exige aprobación no necesita el cálculo.
        if (!publicaciones.esModoInmediato(publicacionId)) {
            throw new ModoReservaNoSoportadoException();
        }
        Cotizacion cotizacion = cotizaciones.cotizar(publicacionId, llegada, salida, servicios);
        Reserva reserva = Reserva.nueva(publicacionId, huespedId, llegada, salida, (int) cotizacion.noches(),
                cotizacion.precioBase(), cotizacion.tarifaLimpieza(), cotizacion.tarifaServicio(),
                cotizacion.descuento(), cotizacion.totalServiciosAdicionales(), cotizacion.total(), servicios,
                ahora.plus(apartado));

        Reserva guardada;
        try {
            // Tras cotizar, que ya validó el rango. La estancia debe caber en un periodo y no estar ocupada
            // (SOF-27); es una verificación temprana: la restricción de exclusión sigue siendo la última
            // barrera ante carreras, y el flush dentro de RegistroReservas la evalúa.
            disponibilidad.exigirDisponible(publicacionId, llegada, salida);
            guardada = registro.registrar(reserva);
        } catch (EstanciaNoDisponibleException error) {
            return perdioLaCarrera(error, huespedId, publicacionId, llegada, salida, servicios);
        } catch (DataIntegrityViolationException error) {
            if (!RestriccionesPostgres.violaExclusion(error, RESTRICCION_SIN_CRUCE)) {
                throw error;
            }
            return perdioLaCarrera(new ReservaSuperpuestaException(), huespedId, publicacionId, llegada, salida,
                    servicios);
        }
        auditar(guardada, huespedId);
        return guardada;
    }

    // Idéntica es la del mismo huésped, publicación, fechas y servicios adicionales. Si hay una vigente con las
    // mismas fechas pero otros servicios, no se devuelve en silencio: se avisa con un 409.
    private Optional<Reserva> buscarIdentica(Long huespedId, Long publicacionId, LocalDate llegada,
            LocalDate salida, Set<Long> servicios) {
        List<Reserva> mismasFechas = reservas.findVigentesIdenticas(huespedId, publicacionId, llegada, salida,
                Instant.now(reloj));
        Optional<Reserva> identica = mismasFechas.stream()
                .filter(reserva -> reserva.getServiciosAdicionalesIds().equals(servicios)).findFirst();
        if (identica.isEmpty() && !mismasFechas.isEmpty()) {
            throw new ReservaConOtrosServiciosException();
        }
        return identica;
    }

    // Dos reintentos idénticos y simultáneos del mismo huésped: uno inserta y el otro choca con la reserva del
    // primero, ya sea contra la restricción de exclusión o en la verificación de disponibilidad. Quien choca
    // contra su propia reserva idéntica debe recibirla (idempotencia), no un 409. La consulta va después de
    // que la transacción de la inserción terminó, así que ve lo que el ganador ya confirmó; si no hay
    // ninguna idéntica, el cruce es con otra reserva y el error original se mantiene.
    private Reserva perdioLaCarrera(RuntimeException error, Long huespedId, Long publicacionId, LocalDate llegada,
            LocalDate salida, Set<Long> servicios) {
        return buscarIdentica(huespedId, publicacionId, llegada, salida, servicios).orElseThrow(() -> error);
    }

    private void auditar(Reserva reserva, Long huespedId) {
        publicador.publicar(new EventoCritico(String.valueOf(huespedId), AccionCritica.RESERVA, ENTIDAD,
                String.valueOf(reserva.getId()), ResultadoEvento.EXITO, Instant.now(reloj),
                Map.of("operacion", "crear", "publicacionId", String.valueOf(reserva.getPublicacionId()))));
    }
}
