package co.edu.unbosque.marketplace.identidad.repository;

import co.edu.unbosque.marketplace.identidad.model.TokenRecuperacion;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Long> {

    /**
     * Códigos sin usar del usuario, del más reciente al más antiguo. El bloqueo
     * impide que dos confirmaciones simultáneas usen el mismo código.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenRecuperacion t where t.usuarioId = :usuarioId and t.usadoEn is null "
            + "order by t.creadoEn desc, t.id desc")
    List<TokenRecuperacion> findPendientesParaActualizar(@Param("usuarioId") Long usuarioId);
}
