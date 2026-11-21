package co.edu.unbosque.marketplace.identidad.services;

/**
 * Lo mínimo que otro módulo necesita saber de un usuario (SOF-36, D5): solo su nombre para
 * mostrarlo, sin correo, teléfono ni ningún otro dato de la cuenta.
 */
public record HuespedBasico(Long id, String nombre) {
}
