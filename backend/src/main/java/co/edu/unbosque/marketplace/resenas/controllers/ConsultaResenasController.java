package co.edu.unbosque.marketplace.resenas.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.resenas.controllers.dto.ResenasPublicacionResponse;
import co.edu.unbosque.marketplace.resenas.services.ConsultaResenasService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de las reseñas y la calificación promedio de una publicación (SOF-81, HU-36). */
@RestController
@RequestMapping("/api/v1/publicaciones")
@PreAuthorize(Permisos.HUESPED)
public class ConsultaResenasController {

    private final ConsultaResenasService consultaResenas;

    public ConsultaResenasController(ConsultaResenasService consultaResenas) {
        this.consultaResenas = consultaResenas;
    }

    /**
     * Las reseñas visibles de la publicación, de la más reciente a la más antigua, con el promedio de un
     * decimal. Sin reseñas, el promedio es nulo y la lista queda vacía.
     */
    @GetMapping("/{id}/resenas")
    public ResenasPublicacionResponse consultar(@PathVariable Long id) {
        return ResenasPublicacionResponse.desde(consultaResenas.consultar(id));
    }
}
