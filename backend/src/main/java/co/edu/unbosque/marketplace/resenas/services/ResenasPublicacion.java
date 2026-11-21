package co.edu.unbosque.marketplace.resenas.services;

import java.util.List;

/**
 * Las reseñas visibles de una publicación, de la más reciente a la más antigua. {@code promedio} es nulo
 * mientras no haya ninguna: quien consume debe indicarlo, no mostrar cero (SOF-81, HU-36).
 */
public record ResenasPublicacion(Double promedio, List<ResenaPublicada> resenas) {
}
