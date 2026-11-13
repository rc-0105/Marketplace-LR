package co.edu.unbosque.marketplace.busqueda.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.CandidatoBusqueda;
import java.math.BigDecimal;
import java.util.List;

public record ResultadoBusquedaResponse(Long id, String titulo, String ciudad, String tipo, Integer capacidad,
        BigDecimal precioNoche, String imagenPrincipal) {

    public static ResultadoBusquedaResponse desde(CandidatoBusqueda candidato) {
        return new ResultadoBusquedaResponse(candidato.id(), candidato.titulo(), candidato.ciudad(),
                candidato.tipo(), candidato.capacidad(), candidato.precioNoche(), candidato.imagenPrincipal());
    }

    public static List<ResultadoBusquedaResponse> desde(List<CandidatoBusqueda> candidatos) {
        return candidatos.stream().map(ResultadoBusquedaResponse::desde).toList();
    }
}
