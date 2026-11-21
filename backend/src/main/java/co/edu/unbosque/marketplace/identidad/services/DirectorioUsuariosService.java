package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nombre básico de una cuenta para otros módulos (SOF-36, D4): a diferencia de {@code CuentaService},
 * que también restringe, elimina y rota contraseñas, este bean solo expone lo mínimo para mostrar al
 * huésped en una lista, sin cruzar el límite de módulos con la entidad {@code Usuario} (ADR-2).
 */
@Service
public class DirectorioUsuariosService {

    private final UsuarioRepository usuarios;

    public DirectorioUsuariosService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Vacío sin ids, sin tocar la base (D7). Un id que ya no existe simplemente no aparece. */
    @Transactional(readOnly = true)
    public Map<Long, HuespedBasico> consultarBasicos(Collection<Long> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        return usuarios.findBasicosByIdIn(usuarioIds).stream()
                .collect(Collectors.toUnmodifiableMap(HuespedBasico::id, basico -> basico));
    }
}
