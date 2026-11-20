package co.edu.unbosque.marketplace.alojamientos.services;

import java.math.BigDecimal;

/** Servicio con costo que el huésped puede agregar a su estancia; el id es el que envía al cotizar. */
public record ServicioAdicionalDetalle(Long id, String nombre, BigDecimal costo) {
}
