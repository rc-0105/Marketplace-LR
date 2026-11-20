package co.edu.unbosque.marketplace.auditoria.services;

import java.time.LocalDate;

/**
 * Filtros opcionales de la consulta de auditoría (RF-59). Un texto vacío o en blanco
 * equivale a un filtro ausente; {@code evento} llega como texto y el servicio lo valida.
 */
public record FiltroAuditoria(
        String usuario,
        String evento,
        String entidad,
        String entidadId,
        LocalDate desde,
        LocalDate hasta) {

    public FiltroAuditoria {
        usuario = nulaSiBlanco(usuario);
        evento = nulaSiBlanco(evento);
        entidad = nulaSiBlanco(entidad);
        entidadId = nulaSiBlanco(entidadId);
    }

    private static String nulaSiBlanco(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
