package co.edu.unbosque.marketplace.resenas.services;

import co.edu.unbosque.marketplace.identidad.services.CuentaService;
import co.edu.unbosque.marketplace.resenas.model.EstadoResena;
import co.edu.unbosque.marketplace.resenas.model.Resena;
import co.edu.unbosque.marketplace.resenas.repository.ResenaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de las reseñas visibles de una publicación y de su calificación promedio (SOF-81, HU-36, RF-54).
 * Las reseñas ocultas por la moderación no cuentan ni en la lista ni en el promedio.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaResenasService {

    private static final String AUTOR_DESCONOCIDO = "Huésped";

    private final ResenaRepository resenas;
    private final CuentaService cuentas;

    public ConsultaResenasService(ResenaRepository resenas, CuentaService cuentas) {
        this.resenas = resenas;
        this.cuentas = cuentas;
    }

    /** Las reseñas visibles, de la más reciente a la más antigua, con el promedio (AC1, AC2, AC3). */
    public ResenasPublicacion consultar(Long publicacionId) {
        List<Resena> visibles = visibles(publicacionId);
        if (visibles.isEmpty()) {
            return new ResenasPublicacion(null, List.of());
        }
        Set<Long> huespedes = new HashSet<>();
        for (Resena resena : visibles) {
            huespedes.add(resena.getHuespedId());
        }
        Map<Long, String> nombres = cuentas.nombresDe(huespedes);

        List<ResenaPublicada> publicadas = new ArrayList<>();
        for (Resena resena : visibles) {
            String autor = nombres.getOrDefault(resena.getHuespedId(), AUTOR_DESCONOCIDO);
            publicadas.add(new ResenaPublicada(resena.getId(), resena.getReservaId(), autor,
                    resena.getCalificacion(), resena.getContenido(), resena.getFecha()));
        }
        return new ResenasPublicacion(promedioDe(visibles), publicadas);
    }

    /** El promedio con un decimal de las reseñas visibles; nulo si no hay ninguna (AC2). */
    public Double promedio(Long publicacionId) {
        List<Resena> visibles = visibles(publicacionId);
        if (visibles.isEmpty()) {
            return null;
        }
        return promedioDe(visibles);
    }

    private List<Resena> visibles(Long publicacionId) {
        return resenas.findByPublicacionIdAndEstadoOrderByFechaDesc(publicacionId, EstadoResena.VISIBLE);
    }

    private Double promedioDe(List<Resena> visibles) {
        int suma = 0;
        for (Resena resena : visibles) {
            suma += resena.getCalificacion();
        }
        return BigDecimal.valueOf(suma)
                .divide(BigDecimal.valueOf(visibles.size()), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
