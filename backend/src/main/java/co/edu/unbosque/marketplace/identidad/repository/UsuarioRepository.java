package co.edu.unbosque.marketplace.identidad.repository;

import co.edu.unbosque.marketplace.identidad.model.EstadoCuenta;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
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

    /** Cuentas cuyo nombre o correo contienen el texto, sin las eliminadas, ordenadas por nombre. */
    @Query("""
            SELECT u FROM Usuario u
            WHERE u.estado <> co.edu.unbosque.marketplace.identidad.model.EstadoCuenta.ELIMINADA
              AND (LOWER(u.nombre) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(u.correo) LIKE LOWER(CONCAT('%', :texto, '%')))
            ORDER BY u.nombre, u.id
            """)
    List<Usuario> buscarParaAdministracion(@Param("texto") String texto, Pageable pagina);

    @Query("SELECT u.id FROM Usuario u WHERE u.id IN :ids AND u.estado = :estado")
    Set<Long> findIdsByIdInAndEstado(@Param("ids") Collection<Long> ids, @Param("estado") EstadoCuenta estado);
}
