package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.TipoAlojamientoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogos de servicios y tipos de alojamiento que una publicación puede elegir (RF-18). */
@Service
public class CatalogoService {

    private final ServicioRepository servicios;
    private final TipoAlojamientoRepository tipos;

    public CatalogoService(ServicioRepository servicios, TipoAlojamientoRepository tipos) {
        this.servicios = servicios;
        this.tipos = tipos;
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
