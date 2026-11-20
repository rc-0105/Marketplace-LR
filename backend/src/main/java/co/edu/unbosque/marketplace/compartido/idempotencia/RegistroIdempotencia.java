package co.edu.unbosque.marketplace.compartido.idempotencia;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia de las claves de idempotencia (SOF-77, V24). Cada operación corre en su propia transacción
 * corta ({@code REQUIRES_NEW}): el reclamo debe quedar confirmado antes de operar, aunque quien llama ya
 * tenga una transacción abierta. Usa SQL constante con parámetros enlazados, sin entidad JPA.
 */
@Repository
public class RegistroIdempotencia {

    // Una sola sentencia atómica: inserta, o reemplaza la fila solo si ya venció. Sin fila devuelta, la
    // clave está tomada y vigente.
    private static final String RECLAMAR = """
            INSERT INTO clave_idempotencia (usuario_id, clave, huella, estado, reclamo, creada_en, vence_en)
            VALUES (:usuario, :clave, :huella, 'EN_PROCESO', :reclamo, :ahora, :lease)
            ON CONFLICT (usuario_id, clave) DO UPDATE SET huella = EXCLUDED.huella, estado = 'EN_PROCESO',
                reclamo = EXCLUDED.reclamo, codigo_estado = NULL, cuerpo = NULL,
                creada_en = EXCLUDED.creada_en, vence_en = EXCLUDED.vence_en
            WHERE clave_idempotencia.vence_en <= :ahora
            RETURNING reclamo
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public RegistroIdempotencia(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Intenta tomar la clave. Devuelve el identificador del reclamo si la tomó (no existía o estaba vencida)
     * y vacío si otro la tiene vigente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> reclamar(Long usuarioId, UUID clave, String huella, Instant ahora, Duration lease) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("usuario", usuarioId)
                .addValue("clave", clave)
                .addValue("huella", huella)
                .addValue("reclamo", UUID.randomUUID())
                .addValue("ahora", utc(ahora))
                .addValue("lease", utc(ahora.plus(lease)));
        List<UUID> reclamos = jdbc.query(RECLAMAR, parametros, (rs, fila) -> rs.getObject("reclamo", UUID.class));
        return reclamos.stream().findFirst();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
