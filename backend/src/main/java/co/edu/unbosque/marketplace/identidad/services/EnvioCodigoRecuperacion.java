package co.edu.unbosque.marketplace.identidad.services;

import java.time.Instant;

/**
 * Puerto por el que el código de recuperación llega al usuario. Hoy no hay envío
 * real de correo (restricción de SOF-14); cambiar el canal no toca el servicio.
 */
public interface EnvioCodigoRecuperacion {

    void enviar(String correo, String codigo, Instant venceEn);
}
