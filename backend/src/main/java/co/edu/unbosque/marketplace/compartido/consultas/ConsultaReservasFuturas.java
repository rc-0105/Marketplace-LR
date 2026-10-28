package co.edu.unbosque.marketplace.compartido.consultas;

/**
 * Puerto con el que {@code identidad} pregunta cuántas reservas bloquean la baja de una cuenta, sin
 * conocer el modelo de reservas (ADR-2).
 *
 * <p>Cuenta las reservas confirmadas que todavía no terminaron, incluidas las que están en curso, de
 * un usuario como anfitrión MÁS como huésped. La definición final de "futura" queda para HU-22.
 *
 * <p>Contrato para HU-22: la implementación real MUST ser un {@code @Service} o {@code @Component}
 * escaneado, no un {@code @Bean} de otra {@code @Configuration}. Mientras no exista, {@link
 * ConfiguracionReservasPorDefecto} registra un bean que devuelve 0 y que se retira solo cuando aparece
 * la implementación real.
 */
public interface ConsultaReservasFuturas {

    long contarConfirmadasFuturas(Long usuarioId);
}
