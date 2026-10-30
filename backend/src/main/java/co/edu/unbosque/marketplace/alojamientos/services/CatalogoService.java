package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.model.PoliticaCancelacion;
import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.TipoAlojamientoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogos de servicios, tipos de alojamiento (RF-18) y políticas de cancelación (RF-20) de una publicación. */
@Service
public class CatalogoService {

    private final ServicioRepository servicios;
    private final TipoAlojamientoRepository tipos;
    private final PoliticaCancelacionRepository politicas;

    public CatalogoService(ServicioRepository servicios, TipoAlojamientoRepository tipos,
            PoliticaCancelacionRepository politicas) {
        this.servicios = servicios;
        this.tipos = tipos;
        this.politicas = politicas;
    }

    /** Políticas de cancelación, de la más flexible a la más estricta (RF-20). */
    @Transactional(readOnly = true)
    public List<PoliticaCancelacion> listarPoliticasCancelacion() {
        return politicas.findAllByOrderByDiasSinCostoAsc();
    }

    /** Todos los servicios, en orden alfabético. */
    @Transactional(readOnly = true)
    public List<Servicio> listarServicios() {
        return servicios.findAllByOrderByNombreAsc();
    }

    /** Todos los tipos de alojamiento, en orden alfabético. */
    @Transactional(readOnly = true)
    public List<TipoAlojamiento> listarTiposAlojamiento() {
        return tipos.findAllByOrderByNombreAsc();
    }
}
