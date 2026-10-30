package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.model.ServicioAdicional;
import java.math.BigDecimal;


public record ServicioAdicionalResponse(Long id, String nombre, BigDecimal costo) {

    public static ServicioAdicionalResponse desde(ServicioAdicional adicional) {
        return new ServicioAdicionalResponse(adicional.getId(), adicional.getNombre(), adicional.getCosto());
    }
}
