package co.edu.unbosque.marketplace.identidad.repository;

import co.edu.unbosque.marketplace.identidad.model.EstadoCuenta;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.services.HuespedBasico;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByCorreo(String correo);

    Optional<Usuario> findByCorreo(String correo);

    /**
     * Cuentas cuyo nombre o correo contienen el texto, sin las eliminadas, ordenadas por nombre. El texto llega
     * con {@code \}, {@code %} y {@code _} escapados para que se busquen literalmente.
     */
    @Query("""
            SELECT u FROM Usuario u
            WHERE u.estado <> co.edu.unbosque.marketplace.identidad.model.EstadoCuenta.ELIMINADA
              AND (LOWER(u.nombre) LIKE LOWER(CONCAT('%', :texto, '%')) ESCAPE '\\'
                   OR LOWER(u.correo) LIKE LOWER(CONCAT('%', :texto, '%')) ESCAPE '\\')
            ORDER BY u.nombre, u.id
            """)
    List<Usuario> buscarParaAdministracion(@Param("texto") String texto, Pageable pagina);

    @Query("SELECT u.id FROM Usuario u WHERE u.id IN :ids AND u.estado = :estado")
    Set<Long> findIdsByIdInAndEstado(@Param("ids") Collection<Long> ids, @Param("estado") EstadoCuenta estado);

    // Nombre básico para otros módulos (SOF-36, D4/D5): nunca el correo ni el teléfono.
    @Query("SELECT new co.edu.unbosque.marketplace.identidad.services.HuespedBasico(u.id, u.nombre)"
            + " FROM Usuario u WHERE u.id IN :ids")
    List<HuespedBasico> findBasicosByIdIn(@Param("ids") Collection<Long> ids);
}
