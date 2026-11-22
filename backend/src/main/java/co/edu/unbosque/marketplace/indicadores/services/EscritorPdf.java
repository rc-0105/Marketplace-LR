package co.edu.unbosque.marketplace.indicadores.services;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reporte en PDF: título, filtros, cantidad de filas y una tabla con el mismo contenido que la
 * pantalla, en A4 horizontal. Los encabezados se repiten en cada página.
 */
@Component
public class EscritorPdf implements EscritorReporte {

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font ENCABEZADO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Color FONDO_ENCABEZADO = new Color(230, 230, 230);

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.PDF;
    }

    @Override
    public byte[] escribir(Reporte reporte) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(documento, salida);
        documento.open();
        documento.add(new Paragraph(reporte.tipo().titulo(), TITULO));
        documento.add(new Paragraph(reporte.descripcionRango(), NORMAL));
        Paragraph filas = new Paragraph("Filas: " + reporte.filas().size(), NORMAL);
        filas.setSpacingAfter(8);
        documento.add(filas);

        List<String> encabezados = reporte.encabezados();
        PdfPTable tabla = new PdfPTable(encabezados.size());
        tabla.setWidthPercentage(100);
        for (String encabezado : encabezados) {
            PdfPCell celda = new PdfPCell(new Phrase(encabezado, ENCABEZADO));
            celda.setBackgroundColor(FONDO_ENCABEZADO);
            tabla.addCell(celda);
        }
        // Una tabla hecha solo de encabezados repetibles no se dibuja; sin filas, va como fila normal.
        tabla.setHeaderRows(reporte.filas().isEmpty() ? 0 : 1);
        for (List<Object> fila : reporte.filas()) {
            for (Object valor : fila) {
                tabla.addCell(new Phrase(texto(valor), NORMAL));
            }
        }
        documento.add(tabla);
        documento.close();
        return salida.toByteArray();
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return "";
        }
        return valor instanceof BigDecimal decimal ? decimal.toPlainString() : valor.toString();
    }
}
