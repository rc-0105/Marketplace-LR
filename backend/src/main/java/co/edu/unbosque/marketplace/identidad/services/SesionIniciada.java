package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.identidad.model.Usuario;

/** Resultado de un login exitoso: la cuenta y el token recién emitido. */
public record SesionIniciada(Usuario usuario, TokenEmitido token) {
}
