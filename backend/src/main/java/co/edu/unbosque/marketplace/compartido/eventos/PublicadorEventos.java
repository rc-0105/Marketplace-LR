package co.edu.unbosque.marketplace.compartido.eventos;

/**
 * Puerto por el que los servicios de aplicación anuncian eventos críticos, sin
 * depender de quién los escucha (ADR-1, ADR-2).
 */
public interface PublicadorEventos {

    void publicar(EventoCritico evento);
}
