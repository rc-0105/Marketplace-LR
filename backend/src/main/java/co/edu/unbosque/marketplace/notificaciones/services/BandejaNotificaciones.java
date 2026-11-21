package co.edu.unbosque.marketplace.notificaciones.services;

import co.edu.unbosque.marketplace.notificaciones.model.Notificacion;
import java.util.List;

/** Las notificaciones más recientes de un usuario y cuántas de todas las suyas siguen sin leer. */
public record BandejaNotificaciones(long noLeidas, List<Notificacion> notificaciones) {
}
