package co.edu.unbosque.marketplace.indicadores.services;

import co.edu.unbosque.marketplace.indicadores.exceptions.TipoReporteDesconocidoException;
import java.util.Arrays;
import java.util.List;

/** Reportes que el administrador puede consultar y exportar (RF-43), con sus columnas en orden. */
public enum TipoReporte {

    USUARIOS("usuarios", "Reporte de usuarios",
            List.of("ID", "Nombre", "Correo", "Rol", "Estado", "Creado")),
    PUBLICACIONES("publicaciones", "Reporte de publicaciones",
            List.of("ID", "Título", "Ciudad", "Tipo", "Estado", "Precio por noche", "Anfitrión", "Creada")),
    RESERVAS("reservas", "Reporte de reservas",
            List.of("ID", "Publicación", "Ciudad", "Huésped", "Llegada", "Salida", "Noches", "Estado", "Total",
                    "Creada")),
    INGRESOS("ingresos", "Reporte de ingresos estimados",
            List.of("Mes", "Reservas pagadas", "Ingresos estimados")),
    DEMANDA("demanda", "Reporte de demanda por ciudad y tipo de alojamiento",
            List.of("Ciudad", "Tipo de alojamiento", "Reservas"));

    private final String ruta;
    private final String titulo;
    private final List<String> encabezados;

    TipoReporte(String ruta, String titulo, List<String> encabezados) {
        this.ruta = ruta;
        this.titulo = titulo;
        this.encabezados = encabezados;
    }

    /** El tipo que nombra el segmento {@code {tipo}} de la ruta; cualquier otro valor responde 400. */
    public static TipoReporte desdeRuta(String ruta) {
        return Arrays.stream(values())
                .filter(tipo -> tipo.ruta.equals(ruta))
                .findFirst()
                .orElseThrow(TipoReporteDesconocidoException::new);
    }

    public String ruta() {
        return ruta;
    }

    public String titulo() {
        return titulo;
    }

    public List<String> encabezados() {
        return encabezados;
    }
}
