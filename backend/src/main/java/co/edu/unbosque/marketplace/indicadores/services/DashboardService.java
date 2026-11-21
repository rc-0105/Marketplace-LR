package co.edu.unbosque.marketplace.indicadores.services;

import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.indicadores.exceptions.RangoFechasInvalidoException;
import co.edu.unbosque.marketplace.indicadores.repository.IndicadoresRepository;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calcula los indicadores clave del negocio para el administrador (RF-41, RF-42). */
@Service
public class DashboardService {

    /** Cuántas ciudades muestra el ranking de demanda. */
    static final int CIUDADES_EN_RANKING = 5;

    private final IndicadoresRepository indicadores;
    private final Clock reloj;

    public DashboardService(IndicadoresRepository indicadores, Clock reloj) {
        this.indicadores = indicadores;
        this.reloj = reloj;
    }

    /** Todos los indicadores sobre el mismo rango; si desde es posterior a hasta, 400. */
    @Transactional(readOnly = true)
    public Dashboard calcular(RangoFechas rango) {
        if (!rango.esCoherente()) {
            throw new RangoFechasInvalidoException();
        }
        return new Dashboard(
                rango,
                completar(indicadores.usuariosPorRol(rango), Arrays.stream(Rol.values()).map(Enum::name).toList()),
                indicadores.publicacionesActivas(rango),
                completar(indicadores.reservasPorEstado(rango, reloj.instant()),
                        Arrays.stream(EstadoReserva.values()).map(Enum::name).toList()),
                indicadores.ingresosEstimados(rango),
                indicadores.ciudadesMayorDemanda(rango, CIUDADES_EN_RANKING));
    }

    /** Todas las claves en el orden dado, con 0 donde la consulta no trajo filas. */
    private static Map<String, Long> completar(Map<String, Long> conteos, Iterable<String> claves) {
        Map<String, Long> completos = new LinkedHashMap<>();
        claves.forEach(clave -> completos.put(clave, conteos.getOrDefault(clave, 0L)));
        return completos;
    }
}
