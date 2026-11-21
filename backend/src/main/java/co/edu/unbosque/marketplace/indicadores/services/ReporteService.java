package co.edu.unbosque.marketplace.indicadores.services;

import co.edu.unbosque.marketplace.indicadores.exceptions.RangoFechasInvalidoException;
import co.edu.unbosque.marketplace.indicadores.repository.ExportacionRepository;
import co.edu.unbosque.marketplace.indicadores.repository.IndicadoresRepository;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reportes del administrador (RF-43, RF-44, HU-28). La pantalla y los archivos salen de la misma
 * consulta, así que un archivo trae exactamente las filas que muestra la pantalla.
 */
@Service
public class ReporteService {

    private final IndicadoresRepository indicadores;
    private final ExportacionRepository exportaciones;
    private final Map<FormatoExportacion, EscritorReporte> escritores = new EnumMap<>(FormatoExportacion.class);
    private final Clock reloj;

    public ReporteService(IndicadoresRepository indicadores, ExportacionRepository exportaciones,
            List<EscritorReporte> escritores, Clock reloj) {
        this.indicadores = indicadores;
        this.exportaciones = exportaciones;
        escritores.forEach(escritor -> this.escritores.put(escritor.formato(), escritor));
        this.reloj = reloj;
    }

    /** El reporte sobre el rango; si desde es posterior a hasta, 400. */
    @Transactional(readOnly = true)
    public Reporte generar(TipoReporte tipo, RangoFechas rango) {
        if (!rango.esCoherente()) {
            throw new RangoFechasInvalidoException();
        }
        return new Reporte(tipo, rango, indicadores.filasReporte(tipo, rango, reloj.instant()));
    }

    /** El reporte escrito en el formato pedido; deja registrado quién lo exportó, cuándo y con qué filtros. */
    @Transactional
    public ArchivoReporte exportar(TipoReporte tipo, FormatoExportacion formato, RangoFechas rango,
            Long administradorId) {
        Reporte reporte = generar(tipo, rango);
        byte[] contenido = escritores.get(formato).escribir(reporte);
        exportaciones.registrar(administradorId, tipo, formato, rango, reporte.filas().size(), reloj.instant());
        return new ArchivoReporte(reporte.nombreArchivo(formato), formato, contenido);
    }
}
