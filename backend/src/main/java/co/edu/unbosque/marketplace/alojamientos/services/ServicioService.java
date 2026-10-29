package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de servicios que una publicación puede ofrecer. */
@Service
public class ServicioService {

    private final ServicioRepository catalogo;

    public ServicioService(ServicioRepository catalogo) {
        this.catalogo = catalogo;
    }

    /** Todo el catálogo, en orden alfabético. */
    @Transactional(readOnly = true)
    public List<Servicio> listar() {
        return catalogo.findAllByOrderByNombreAsc();
    }
}
