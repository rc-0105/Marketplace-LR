package co.edu.unbosque.marketplace.indicadores.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Reporte en Excel: una hoja con la fila de encabezados fija y una fila por fila del reporte. Los
 * números quedan como celdas numéricas para que la gerencia pueda sumarlos.
 */
@Component
public class EscritorExcel implements EscritorReporte {

    /** Ancho máximo de columna, en caracteres. */
    private static final int ANCHO_MAXIMO = 60;

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.XLSX;
    }

    @Override
    public byte[] escribir(Reporte reporte) {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(reporte.tipo().ruta());
            CellStyle negrita = libro.createCellStyle();
            Font fuente = libro.createFont();
            fuente.setBold(true);
            negrita.setFont(fuente);
            CellStyle dinero = libro.createCellStyle();
            dinero.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));

            List<String> encabezados = reporte.encabezados();
            int[] anchos = new int[encabezados.size()];
            Row primera = hoja.createRow(0);
            for (int columna = 0; columna < encabezados.size(); columna++) {
                Cell celda = primera.createCell(columna);
                celda.setCellValue(encabezados.get(columna));
                celda.setCellStyle(negrita);
                anchos[columna] = encabezados.get(columna).length();
            }
            int numero = 1;
            for (List<Object> fila : reporte.filas()) {
                Row renglon = hoja.createRow(numero++);
                for (int columna = 0; columna < fila.size(); columna++) {
                    Object valor = fila.get(columna);
                    // Las celdas vacías se crean igual, para que cada fila tenga todas sus columnas.
                    Cell celda = renglon.createCell(columna);
                    if (valor instanceof BigDecimal decimal) {
                        celda.setCellValue(decimal.doubleValue());
                        celda.setCellStyle(dinero);
                    } else if (valor instanceof Number entero) {
                        celda.setCellValue(entero.doubleValue());
                    } else if (valor != null) {
                        celda.setCellValue(valor.toString());
                    }
                    anchos[columna] = Math.max(anchos[columna], valor == null ? 0 : valor.toString().length());
                }
            }
            for (int columna = 0; columna < anchos.length; columna++) {
                hoja.setColumnWidth(columna, (Math.min(anchos[columna], ANCHO_MAXIMO) + 2) * 256);
            }
            hoja.createFreezePane(0, 1);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el reporte en Excel", e);
        }
    }
}
