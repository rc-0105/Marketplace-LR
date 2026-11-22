package co.edu.unbosque.marketplace.indicadores.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pruebas HTTP de la exportación de reportes (SOF-39, RF-43, RF-44). La consulta en pantalla es el
 * mismo endpoint sin {@code formato}; cada archivo se compara contra ella celda por celda.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReporteIntegracionTest {

    private static final String RUTA = "/api/v1/admin/reportes/";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CONTRASENA = "Segura#2026";
    private static final String ADMINISTRADOR = "admin@correo.com";
    private static final String ANFITRION = "luis@correo.com";
    private static final String HUESPED = "ana@correo.com";
    private static final String RANGO = "desde=2026-11-01&hasta=2026-11-30";
    private static final Instant EN_RANGO = Instant.parse("2026-11-10T15:00:00Z");
    private static final Instant FUERA_DE_RANGO = Instant.parse("2026-10-10T15:00:00Z");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PublicacionRepository publicaciones;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private PasswordEncoder codificador;

    private Long administrador;
    private Long anfitrion;
    private Long huesped;
    private int reservasInsertadas;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM exportacion");
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
        anfitrion = crearUsuario("Luis Pardo", ANFITRION, Rol.ANFITRION);
        huesped = crearUsuario("Ana Gómez", HUESPED, Rol.HUESPED);
        administrador = crearUsuario("Admin", ADMINISTRADOR, Rol.ADMINISTRADOR);
    }

    // Las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM exportacion");
        reservas.deleteAll();
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("CA1: el Excel del reporte de reservas trae exactamente las filas de la consulta en pantalla")
    void excelTraeLasMismasFilasQueLaPantalla() throws Exception {
        sembrarReservas();
        String token = token(ADMINISTRADOR);

        JsonNode pantalla = pantalla("reservas", RANGO, token);
        assertThat(pantalla.get("filas")).hasSize(2);

        byte[] archivo = exportar("reservas", "xlsx", RANGO, token)
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-reservas-2026-11-01-a-2026-11-30.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();

        List<List<String>> hoja = leerExcel(archivo);
        assertThat(hoja.get(0)).isEqualTo(textos(pantalla.get("encabezados")));
        assertThat(hoja.subList(1, hoja.size())).isEqualTo(filasDePantalla(pantalla));
    }

    @Test
    @DisplayName("CA2: el PDF trae el mismo contenido que la consulta en pantalla")
    void pdfTraeElMismoContenido() throws Exception {
        sembrarReservas();
        String token = token(ADMINISTRADOR);

        JsonNode pantalla = pantalla("reservas", RANGO, token);
        byte[] archivo = exportar("reservas", "pdf", RANGO, token)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-reservas-2026-11-01-a-2026-11-30.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        String texto = leerPdf(archivo);
        assertThat(texto).contains("Reporte de reservas", "Desde 2026-11-01 hasta 2026-11-30", "Filas: 2");
        textos(pantalla.get("encabezados")).forEach(encabezado -> assertThat(texto).contains(encabezado));
        filasDePantalla(pantalla).forEach(fila -> fila.stream().filter(celda -> !celda.isEmpty())
                .forEach(celda -> assertThat(texto).contains(celda)));
        assertThat(texto).doesNotContain("Reserva vieja");
    }

    @Test
    @DisplayName("CA3: cada exportación registra quién, cuándo, el tipo, el formato y los filtros; la pantalla no")
    void exportarRegistraLaAuditoria() throws Exception {
        sembrarReservas();
        String token = token(ADMINISTRADOR);
        Instant antes = Instant.now().minusSeconds(1);

        pantalla("reservas", RANGO, token);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exportacion", Long.class)).isZero();

        exportar("reservas", "xlsx", RANGO, token).andExpect(status().isOk());
        exportar("usuarios", "pdf", "", token).andExpect(status().isOk());

        List<Map<String, Object>> registros = jdbc.queryForList(
                "SELECT * FROM exportacion ORDER BY id");
        assertThat(registros).hasSize(2);
        Map<String, Object> excel = registros.get(0);
        assertThat(excel.get("administrador_id")).isEqualTo(administrador);
        assertThat(excel.get("administrador_correo")).isEqualTo(ADMINISTRADOR);
        assertThat(excel.get("tipo")).isEqualTo("RESERVAS");
        assertThat(excel.get("formato")).isEqualTo("XLSX");
        assertThat(excel.get("desde").toString()).isEqualTo("2026-11-01");
        assertThat(excel.get("hasta").toString()).isEqualTo("2026-11-30");
        assertThat(excel.get("filas")).isEqualTo(2);
        assertThat(((Timestamp) excel.get("creado_en")).toInstant()).isBetween(antes, Instant.now());

        Map<String, Object> pdf = registros.get(1);
        assertThat(pdf.get("tipo")).isEqualTo("USUARIOS");
        assertThat(pdf.get("formato")).isEqualTo("PDF");
        assertThat(pdf.get("desde")).isNull();
        assertThat(pdf.get("hasta")).isNull();
        assertThat(pdf.get("filas")).isEqualTo(3);
    }

    @Test
    @DisplayName("CA4: un rango sin registros genera archivos con encabezados y sin filas")
    void rangoVacioGeneraArchivoConEncabezados() throws Exception {
        sembrarReservas();
        String token = token(ADMINISTRADOR);
        String vacio = "desde=2025-01-01&hasta=2025-01-31";

        JsonNode pantalla = pantalla("reservas", vacio, token);
        assertThat(pantalla.get("filas")).isEmpty();

        List<List<String>> hoja = leerExcel(exportar("reservas", "xlsx", vacio, token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(hoja).containsExactly(textos(pantalla.get("encabezados")));

        String texto = leerPdf(exportar("reservas", "pdf", vacio, token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(texto).contains("Filas: 0");
        textos(pantalla.get("encabezados")).forEach(encabezado -> assertThat(texto).contains(encabezado));
        assertThat(jdbc.queryForObject("SELECT filas FROM exportacion WHERE formato = 'XLSX'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("RF-43: usuarios lista las cuentas no eliminadas creadas en el rango")
    void reporteDeUsuarios() throws Exception {
        Long eliminada = crearUsuario("Pedro Gil", "pedro@correo.com", Rol.HUESPED);
        jdbc.update("""
                UPDATE usuario SET estado = 'ELIMINADA', nombre = 'Usuario eliminado',
                    correo = 'eliminada-' || CAST(id AS TEXT) || '@eliminada.invalid' WHERE id = ?
                """, eliminada);
        fecharUsuario(huesped, Instant.parse("2026-11-05T03:30:00Z"));
        fecharUsuario(anfitrion, FUERA_DE_RANGO);
        fecharUsuario(administrador, FUERA_DE_RANGO);
        fecharUsuario(eliminada, EN_RANGO);

        consultar("usuarios", RANGO, token(ADMINISTRADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("usuarios"))
                .andExpect(jsonPath("$.titulo").value("Reporte de usuarios"))
                .andExpect(jsonPath("$.desde").value("2026-11-01"))
                .andExpect(jsonPath("$.hasta").value("2026-11-30"))
                .andExpect(jsonPath("$.encabezados").value(contains("ID", "Nombre", "Correo", "Rol", "Estado",
                        "Creado")))
                .andExpect(jsonPath("$.filas").value(hasSize(1)))
                // 03:30 UTC del 5 es el 4 de noviembre a las 22:30 en Bogotá.
                .andExpect(jsonPath("$.filas[0]").value(contains(huesped.intValue(), "Ana Gómez", HUESPED,
                        "HUESPED", "ACTIVA", "2026-11-04 22:30")));
    }

    @Test
    @DisplayName("RF-43: publicaciones lista todas las creadas en el rango con su tipo y anfitrión")
    void reporteDePublicaciones() throws Exception {
        Long casa = crearPublicacion("Casa del lago", "Bogotá", "Casa", "ACTIVA");
        Long vieja = crearPublicacion("Casa vieja", "Cali", null, "PAUSADA");
        fecharPublicacion(casa, EN_RANGO);
        fecharPublicacion(vieja, FUERA_DE_RANGO);
        jdbc.update("UPDATE publicacion SET precio_noche = 150000 WHERE id = ?", casa);

        consultar("publicaciones", RANGO, token(ADMINISTRADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encabezados").value(contains("ID", "Título", "Ciudad", "Tipo", "Estado",
                        "Precio por noche", "Anfitrión", "Creada")))
                .andExpect(jsonPath("$.filas").value(hasSize(1)))
                .andExpect(jsonPath("$.filas[0][0]").value(casa.intValue()))
                .andExpect(jsonPath("$.filas[0][1]").value("Casa del lago"))
                .andExpect(jsonPath("$.filas[0][2]").value("Bogotá"))
                .andExpect(jsonPath("$.filas[0][3]").value("Casa"))
                .andExpect(jsonPath("$.filas[0][4]").value("ACTIVA"))
                .andExpect(jsonPath("$.filas[0][5]").value(150000.00))
                .andExpect(jsonPath("$.filas[0][6]").value(ANFITRION))
                .andExpect(jsonPath("$.filas[0][7]").value("2026-11-10 10:00"));
    }

    @Test
    @DisplayName("RF-43: ingresos suma por mes solo las reservas pagadas creadas en el rango")
    void reporteDeIngresos() throws Exception {
        Long publicacion = crearPublicacion("Casa del lago", "Bogotá", "Casa", "ACTIVA");
        fecharReserva(insertarReserva(publicacion, "CONFIRMADA", 100_000), Instant.parse("2026-10-15T15:00:00Z"));
        fecharReserva(insertarReserva(publicacion, "COMPLETADA", 200_000), EN_RANGO);
        fecharReserva(insertarReserva(publicacion, "EN_REVISION_MANUAL", 50_000), EN_RANGO);
        fecharReserva(insertarReserva(publicacion, "CANCELADA", 999_000), EN_RANGO);

        consultar("ingresos", "desde=2026-10-01&hasta=2026-11-30", token(ADMINISTRADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encabezados").value(contains("Mes", "Reservas pagadas",
                        "Ingresos estimados")))
                .andExpect(jsonPath("$.filas").value(hasSize(2)))
                .andExpect(jsonPath("$.filas[0][0]").value("2026-10"))
                .andExpect(jsonPath("$.filas[0][1]").value(1))
                .andExpect(jsonPath("$.filas[0][2]").value(100000.00))
                .andExpect(jsonPath("$.filas[1][0]").value("2026-11"))
                .andExpect(jsonPath("$.filas[1][1]").value(2))
                .andExpect(jsonPath("$.filas[1][2]").value(250000.00));
    }

    @Test
    @DisplayName("RF-43: demanda cuenta las reservas por ciudad y tipo de alojamiento, la mayor primero")
    void reporteDeDemanda() throws Exception {
        Long bogotaCasa = crearPublicacion("Casa del lago", "Bogotá", "Casa", "ACTIVA");
        Long bogotaApto = crearPublicacion("Apto centro", " Bogotá ", "Apartamento", "ACTIVA");
        Long sinCiudad = crearPublicacion("Borrador", null, null, "BORRADOR");
        insertarReserva(bogotaApto, "CONFIRMADA", 1);
        insertarReserva(bogotaApto, "CANCELADA", 1);
        insertarReserva(bogotaCasa, "PENDIENTE", 1);
        insertarReserva(sinCiudad, "VENCIDA", 1);

        consultar("demanda", "", token(ADMINISTRADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").doesNotExist())
                .andExpect(jsonPath("$.encabezados").value(contains("Ciudad", "Tipo de alojamiento", "Reservas")))
                .andExpect(jsonPath("$.filas").value(hasSize(3)))
                .andExpect(jsonPath("$.filas[0]").value(contains("Bogotá", "Apartamento", 2)))
                .andExpect(jsonPath("$.filas[1]").value(contains("Bogotá", "Casa", 1)))
                .andExpect(jsonPath("$.filas[2]").value(contains("Sin ciudad", "Sin tipo", 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuarios", "publicaciones", "reservas", "ingresos", "demanda"})
    @DisplayName("RF-43: los cinco reportes se exportan en Excel y en PDF")
    void todosLosReportesSeExportan(String tipo) throws Exception {
        String token = token(ADMINISTRADOR);
        exportar(tipo, "xlsx", "", token)
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-" + tipo + "-historico.xlsx\""));
        exportar(tipo, "pdf", "", token)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("un tipo de reporte desconocido responde 400")
    void tipoDesconocidoResponde400() throws Exception {
        exportar("pagos", "xlsx", "", token(ADMINISTRADOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Tipo de reporte desconocido. Use usuarios, publicaciones, reservas, ingresos o demanda."));
    }

    @Test
    @DisplayName("un formato desconocido responde 400 y no registra la exportación")
    void formatoDesconocidoResponde400() throws Exception {
        exportar("reservas", "csv", "", token(ADMINISTRADOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Formato de exportación desconocido. Use pdf o xlsx."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exportacion", Long.class)).isZero();
    }

    @Test
    @DisplayName("un rango invertido responde 400 y no registra la exportación")
    void rangoInvertidoResponde400() throws Exception {
        exportar("reservas", "xlsx", "desde=2026-11-13&hasta=2026-11-12", token(ADMINISTRADOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha desde no puede ser posterior a la fecha hasta."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exportacion", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {ANFITRION, HUESPED})
    @DisplayName("solo el administrador consulta y exporta reportes")
    void otrosRolesReciben403(String correo) throws Exception {
        String token = token(correo);
        consultar("reservas", "", token).andExpect(status().isForbidden());
        exportar("reservas", "xlsx", "", token).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sin sesión responde 401")
    void sinSesionResponde401() throws Exception {
        mvc.perform(get(RUTA + "reservas?formato=xlsx")).andExpect(status().isUnauthorized());
    }

    /** Dos reservas en noviembre (pantalla y archivos) y una de octubre que el rango deja fuera. */
    private void sembrarReservas() {
        Long bogota = crearPublicacion("Casa del lago", "Bogotá", "Casa", "ACTIVA");
        Long cali = crearPublicacion("Reserva vieja", "Cali", "Apartamento", "ACTIVA");
        fecharReserva(insertarReserva(bogota, "CONFIRMADA", 250_000), EN_RANGO);
        fecharReserva(insertarReserva(bogota, "CANCELADA", 120_000), EN_RANGO.plus(1, ChronoUnit.DAYS));
        fecharReserva(insertarReserva(cali, "COMPLETADA", 90_000), FUERA_DE_RANGO);
    }

    private JsonNode pantalla(String tipo, String filtros, String token) throws Exception {
        String cuerpo = consultar(tipo, filtros, token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cuerpo);
    }

    private ResultActions consultar(String tipo, String filtros, String token) throws Exception {
        return mvc.perform(get(RUTA + tipo + "?" + filtros).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions exportar(String tipo, String formato, String filtros, String token) throws Exception {
        return mvc.perform(get(RUTA + tipo + "?formato=" + formato + "&" + filtros)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static List<String> textos(JsonNode arreglo) {
        List<String> textos = new ArrayList<>();
        arreglo.forEach(nodo -> textos.add(texto(nodo)));
        return textos;
    }

    private static List<List<String>> filasDePantalla(JsonNode pantalla) {
        List<List<String>> filas = new ArrayList<>();
        pantalla.get("filas").forEach(fila -> filas.add(textos(fila)));
        return filas;
    }

    /** Números sin ceros de sobra, para comparar 250000.00 de JSON con 250000 de la celda numérica. */
    private static String texto(JsonNode nodo) {
        if (nodo.isNull()) {
            return "";
        }
        return nodo.isNumber() ? nodo.decimalValue().stripTrailingZeros().toPlainString() : nodo.asText();
    }

    private static List<List<String>> leerExcel(byte[] archivo) throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            Sheet hoja = libro.getSheetAt(0);
            List<List<String>> filas = new ArrayList<>();
            for (Row fila : hoja) {
                List<String> celdas = new ArrayList<>();
                for (int i = 0; i < fila.getLastCellNum(); i++) {
                    celdas.add(texto(fila.getCell(i)));
                }
                filas.add(celdas);
            }
            return filas;
        }
    }

    private static String texto(Cell celda) {
        if (celda == null || celda.getCellType() == CellType.BLANK) {
            return "";
        }
        if (celda.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(celda.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return celda.getStringCellValue();
    }

    private static String leerPdf(byte[] archivo) throws Exception {
        PdfReader lector = new PdfReader(archivo);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina)).append('\n');
            }
            return texto.toString();
        } finally {
            lector.close();
        }
    }

    private Long crearUsuario(String nombre, String correo, Rol rol) {
        return usuarios.save(Usuario.nuevo(nombre, correo, codificador.encode(CONTRASENA), rol)).getId();
    }

    private Long crearPublicacion(String titulo, String ciudad, String tipo, String estado) {
        return jdbc.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, titulo, ciudad, tipo_id, estado)
                VALUES (?, ?, ?, (SELECT id FROM tipo_alojamiento WHERE nombre = ?), ?) RETURNING id
                """, Long.class, anfitrion, titulo, ciudad, tipo, estado);
    }

    // Igual que DashboardIntegracionTest: estado_pago queda SIN_PAGO y cada reserva ocupa noches distintas.
    private long insertarReserva(Long publicacionId, String estado, long total) {
        LocalDate llegada = LocalDate.of(2027, 1, 1).plusDays(3L * reservasInsertadas++);
        Instant venceEn = "PENDIENTE".equals(estado) ? Instant.now().plus(10, ChronoUnit.MINUTES)
                : Instant.now().minus(1, ChronoUnit.DAYS);
        return jdbc.queryForObject("""
                INSERT INTO reserva (publicacion_id, huesped_id, llegada, salida, estado, noches, precio_base,
                    tarifa_limpieza, tarifa_servicio, descuento, total_servicios_adicionales, total, vence_en)
                VALUES (?, ?, ?, ?, ?, 2, ?, 0, 0, 0, 0, ?, ?) RETURNING id
                """, Long.class, publicacionId, huesped, llegada, llegada.plusDays(2), estado, total, total,
                Timestamp.from(venceEn));
    }

    private void fecharUsuario(Long id, Instant fecha) {
        jdbc.update("UPDATE usuario SET creado_en = ? WHERE id = ?", Timestamp.from(fecha), id);
    }

    private void fecharPublicacion(Long id, Instant fecha) {
        jdbc.update("UPDATE publicacion SET creado_en = ? WHERE id = ?", Timestamp.from(fecha), id);
    }

    private void fecharReserva(long id, Instant fecha) {
        jdbc.update("UPDATE reserva SET creado_en = ? WHERE id = ?", Timestamp.from(fecha), id);
    }

    private String token(String correo) throws Exception {
        String cuerpo = """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, CONTRASENA);
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}
