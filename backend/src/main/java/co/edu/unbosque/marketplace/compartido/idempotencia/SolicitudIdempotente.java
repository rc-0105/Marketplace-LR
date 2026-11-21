package co.edu.unbosque.marketplace.compartido.idempotencia;

/**
 * Lo que identifica una solicitud idempotente: quién la hace, la clave que envió, qué operación es y su
 * contenido en forma canónica (el mismo texto para solicitudes semánticamente iguales). Sin dependencias
 * de ningún dominio, para que cualquier endpoint la reutilice (REQ-IS12).
 */
public record SolicitudIdempotente(Long usuarioId, String clave, String operacion, String contenidoCanonico) {
}
