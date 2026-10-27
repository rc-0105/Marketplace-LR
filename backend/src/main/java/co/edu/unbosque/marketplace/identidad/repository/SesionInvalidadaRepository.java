package co.edu.unbosque.marketplace.identidad.repository;

import co.edu.unbosque.marketplace.identidad.model.SesionInvalidada;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionInvalidadaRepository extends JpaRepository<SesionInvalidada, String> {

    /**
     * Inserta sin fallar si el jti ya existe. Atrapar la clave duplicada dentro de una
     * transacción no sirve: en PostgreSQL la deja abortada.
     */
    @Modifying
    @Query(value = """
            INSERT INTO sesion_invalidada (jti, usuario_id, expira_en)
            VALUES (:jti, :usuarioId, :expiraEn)
            ON CONFLICT (jti) DO NOTHING
            """, nativeQuery = true)
    void insertarSiAusente(
            @Param("jti") String jti,
            @Param("usuarioId") Long usuarioId,
            @Param("expiraEn") Instant expiraEn);

    @Modifying
    @Query("DELETE FROM SesionInvalidada s WHERE s.expiraEn < :limite")
    int eliminarVencidasAntesDe(@Param("limite") Instant limite);
}
