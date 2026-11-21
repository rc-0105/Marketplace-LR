package co.edu.unbosque.marketplace.resenas.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.resenas.controllers.dto.OcultarResenaRequest;
import co.edu.unbosque.marketplace.resenas.controllers.dto.ResenaOcultadaResponse;
import co.edu.unbosque.marketplace.resenas.controllers.dto.ResenaParaModeracionResponse;
import co.edu.unbosque.marketplace.resenas.services.ConsultaResenasService;
import co.edu.unbosque.marketplace.resenas.services.ModeracionResenasService;
import co.edu.unbosque.marketplace.resenas.services.ResenaParaModeracion;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Moderación de reseñas inapropiadas por el administrador (SOF-82, HU-37). */
@RestController
@RequestMapping("/api/v1/admin/resenas")
@PreAuthorize(Permisos.ADMINISTRADOR)
public class ModeracionResenasController {

    private final ModeracionResenasService moderacion;
    private final ConsultaResenasService consulta;

    public ModeracionResenasController(ModeracionResenasService moderacion, ConsultaResenasService consulta) {
        this.moderacion = moderacion;
        this.consulta = consulta;
    }

    /** Las reseñas visibles de toda la plataforma, de la más reciente a la más antigua. */
    @GetMapping
    public List<ResenaParaModeracionResponse> listar() {
        List<ResenaParaModeracionResponse> respuesta = new ArrayList<>();
        for (ResenaParaModeracion resena : consulta.visiblesParaModeracion()) {
            respuesta.add(ResenaParaModeracionResponse.desde(resena));
        }
        return respuesta;
    }

    /** Oculta la reseña con el motivo dado; el administrador sale del token, nunca del cuerpo. */
    @PostMapping("/{id}/ocultar")
    public ResenaOcultadaResponse ocultar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody OcultarResenaRequest data) {
        return ResenaOcultadaResponse.desde(moderacion.ocultar(id, Long.valueOf(jwt.getSubject()), data.motivo()));
    }
}
