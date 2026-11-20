package co.edu.unbosque.marketplace.auditoria.controllers.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Una página de resultados con sus totales, sin exponer el tipo de Spring Data (REQ-CA1, CA4). */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaResponse<T> desde(Page<E> pagina, Function<E, T> conversion) {
        return new PaginaResponse<>(pagina.getContent().stream().map(conversion).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }
}
