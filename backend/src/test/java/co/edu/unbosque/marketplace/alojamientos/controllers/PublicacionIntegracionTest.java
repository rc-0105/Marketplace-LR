package co.edu.unbosque.marketplace.alojamientos.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.TipoAlojamientoRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Recorre la creación, consulta, edición y eliminación: token → autorización → validación → servicio → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicacionIntegracionTest {

    private static final String RUTA = "/api/v1/publicaciones";
    private static final String CONTRASENA = "Segura#2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PublicacionRepository publicaciones;

    @Autowired
    private ServicioRepository catalogo;

    @Autowired
    private TipoAlojamientoRepository tipos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ObjectMapper json;

    private Usuario anfitrion;

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        anfitrion = usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com",
                codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    // Las publicaciones referencian al usuario: si quedan, los tests de otros
    // módulos no pueden borrar sus usuarios en su propia preparación.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("Un anfitrión crea el borrador: responde 201 y queda guardado a su nombre")
    void anfitrionCreaBorrador() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa", "tipo": %d, "capacidad": 4}
                """.formatted(idTipo("Casa")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.anfitrionId").value(anfitrion.getId().intValue()))
                .andExpect(jsonPath("$.titulo").value("Casa en la playa"))
                .andExpect(jsonPath("$.tipo.nombre").value("Casa"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.camposFaltantes[0]").value("descripcion"));

        assertThat(publicaciones.count()).isEqualTo(1);
        Publicacion guardada = publicaciones.findAll().get(0);
        assertThat(guardada.getAnfitrionId()).isEqualTo(anfitrion.getId());
    }

    @Test
    @DisplayName("Un huésped no puede crear publicaciones: responde 403 y no guarda nada")
    void huespedRecibe403() throws Exception {
        String token = iniciarSesion("ana@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa"}
                """).andExpect(status().isForbidden());

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Sin token responde 401 y no guarda nada")
    void sinTokenRecibe401() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"titulo": "Casa en la playa"}
                        """))
                .andExpect(status().isUnauthorized());

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Sin título responde 400 y no guarda nada")
    void sinTituloRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "  "}
                """).andExpect(status().isBadRequest());

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Con capacidad cero responde 400, señala el campo capacidad y no guarda nada")
    void capacidadCeroRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa", "capacidad": 0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.capacidad").value("La capacidad debe ser mayor que cero."));

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Con precio por noche negativo responde 400, señala el campo precioNoche y no guarda nada")
    void precioNegativoRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa", "precioNoche": -1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precioNoche").value("El precio por noche debe ser mayor que cero."));

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Con un tipo inexistente responde 400 con el mensaje del tipo")
    void tipoInexistenteRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa", "tipo": 999999}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El tipo de alojamiento no existe en el catálogo."));

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("El dueño edita su publicación: responde 200, guarda los cambios y registra quién editó y cuándo")
    void duenoEditaSuPublicacion() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = idCreado(token);

        editar(token, id, """
                {"titulo": "Casa renovada", "tipo": %d, "precioNoche": 300000}
                """.formatted(idTipo("Apartamento")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Casa renovada"))
                .andExpect(jsonPath("$.tipo.id").value(idTipo("Apartamento").intValue()))
                .andExpect(jsonPath("$.tipo.nombre").value("Apartamento"))
                .andExpect(jsonPath("$.anfitrionId").value(anfitrion.getId().intValue()))
                .andExpect(jsonPath("$.editadoPor").value(anfitrion.getId().intValue()))
                .andExpect(jsonPath("$.editadoEn").isNotEmpty());

        Publicacion guardada = publicaciones.findById(id).orElseThrow();
        assertThat(guardada.getTitulo()).isEqualTo("Casa renovada");
        assertThat(guardada.getPrecioNoche()).isEqualByComparingTo("300000");
        assertThat(guardada.getEditadoPor()).isEqualTo(anfitrion.getId());
        assertThat(guardada.getEditadoEn()).isNotNull();
    }

    @Test
    @DisplayName("El anfitrión lista solo sus publicaciones, la más reciente primero")
    void anfitrionListaSoloLasSuyas() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        idCreado(token);
        crear(token, """
                {"titulo": "Apartamento en el centro"}
                """).andExpect(status().isCreated());
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));
        crear(iniciarSesion("marta@correo.com"), """
                {"titulo": "Casa ajena"}
                """).andExpect(status().isCreated());

        mvc.perform(get(RUTA + "/mias").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].titulo").value("Apartamento en el centro"))
                .andExpect(jsonPath("$[1].titulo").value("Casa en la playa"))
                .andExpect(jsonPath("$[1].tipo.nombre").value("Casa"));
    }

    @Test
    @DisplayName("Un huésped no tiene publicaciones que listar: responde 403")
    void huespedNoListaPublicaciones() throws Exception {
        mvc.perform(get(RUTA + "/mias").header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Otro anfitrión intenta editar la publicación: responde 403 y la publicación no cambia")
    void otroAnfitrionRecibe403() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));

        editar(iniciarSesion("marta@correo.com"), id, """
                {"titulo": "Casa ajena"}
                """).andExpect(status().isForbidden());

        Publicacion guardada = publicaciones.findById(id).orElseThrow();
        assertThat(guardada.getTitulo()).isEqualTo("Casa en la playa");
        assertThat(guardada.getEditadoPor()).isNull();
    }

    @Test
    @DisplayName("El administrador edita la publicación de un anfitrión: responde 200 y queda como editor")
    void administradorEditaCualquierPublicacion() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        Usuario administrador = usuarios.save(Usuario.nuevo("Admin LR", "admin@correo.com",
                codificador.encode(CONTRASENA), Rol.ADMINISTRADOR));

        editar(iniciarSesion("admin@correo.com"), id, """
                {"titulo": "Título corregido"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Título corregido"))
                .andExpect(jsonPath("$.anfitrionId").value(anfitrion.getId().intValue()))
                .andExpect(jsonPath("$.editadoPor").value(administrador.getId().intValue()));
    }

    @Test
    @DisplayName("Un huésped no puede editar publicaciones: responde 403 y la publicación no cambia")
    void huespedNoPuedeEditar() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));

        editar(iniciarSesion("ana@correo.com"), id, """
                {"titulo": "Casa ajena"}
                """).andExpect(status().isForbidden());

        assertThat(publicaciones.findById(id).orElseThrow().getTitulo()).isEqualTo("Casa en la playa");
    }

    @Test
    @DisplayName("Editar una publicación que no existe responde 404")
    void publicacionInexistenteRecibe404() throws Exception {
        editar(iniciarSesion("luis@correo.com"), 999_999L, """
                {"titulo": "Casa renovada"}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No encontramos esa publicación."));
    }

    @Test
    @DisplayName("Editar sin título responde 400 y la publicación no cambia")
    void editarSinTituloRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = idCreado(token);

        editar(token, id, """
                {"titulo": " "}
                """).andExpect(status().isBadRequest());

        assertThat(publicaciones.findById(id).orElseThrow().getTitulo()).isEqualTo("Casa en la playa");
    }

    @Test
    @DisplayName("Al editar se reemplazan los servicios del catálogo y las imágenes, en el orden enviado")
    void editarReemplazaServiciosEImagenes() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long wifi = idServicio("Wifi");
        Long piscina = idServicio("Piscina");
        Long cocina = idServicio("Cocina");
        String creada = crear(token, """
                {"titulo": "Casa en la playa", "servicios": [%d, %d],
                 "imagenes": ["https://imagenes.com/1.jpg", "https://imagenes.com/2.jpg"]}
                """.formatted(wifi, piscina))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.servicios[0].nombre").value("Piscina"))
                .andExpect(jsonPath("$.servicios[1].nombre").value("Wifi"))
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(creada).get("id").asLong();

        editar(token, id, """
                {"titulo": "Casa en la playa", "servicios": [%d],
                 "imagenes": ["https://imagenes.com/3.jpg", "https://imagenes.com/1.jpg"]}
                """.formatted(cocina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios.length()").value(1))
                .andExpect(jsonPath("$.servicios[0].id").value(cocina.intValue()))
                .andExpect(jsonPath("$.servicios[0].nombre").value("Cocina"))
                .andExpect(jsonPath("$.imagenes[0]").value("https://imagenes.com/3.jpg"))
                .andExpect(jsonPath("$.imagenes[1]").value("https://imagenes.com/1.jpg"));

        Publicacion guardada = publicaciones.findById(id).orElseThrow();
        assertThat(guardada.getServicios()).extracting(Servicio::getNombre).containsExactly("Cocina");
        assertThat(guardada.getImagenes()).containsExactly("https://imagenes.com/3.jpg",
                "https://imagenes.com/1.jpg");
    }

    @Test
    @DisplayName("Con un servicio que no está en el catálogo responde 400 y no guarda nada")
    void servicioFueraDelCatalogoRecibe400() throws Exception {
        crear(iniciarSesion("luis@correo.com"), """
                {"titulo": "Casa en la playa", "servicios": [999999]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Uno de los servicios no existe en el catálogo."));

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("El catálogo de servicios se lista con sesión y en orden alfabético")
    void listaElCatalogoDeServicios() throws Exception {
        mvc.perform(get("/api/v1/catalogos/servicios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Aire acondicionado"))
                .andExpect(jsonPath("$[?(@.nombre == 'Wifi')].id").isNotEmpty());
    }

    @Test
    @DisplayName("El catálogo de tipos de alojamiento se lista con sesión y en orden alfabético")
    void listaElCatalogoDeTipos() throws Exception {
        mvc.perform(get("/api/v1/catalogos/tipos-alojamiento")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Apartamento"))
                .andExpect(jsonPath("$[1].nombre").value("Casa"))
                .andExpect(jsonPath("$[2].nombre").value("Habitación"));
    }

    @Test
    @DisplayName("Sin token el catálogo de tipos de alojamiento responde 401")
    void catalogoDeTiposSinTokenRecibe401() throws Exception {
        mvc.perform(get("/api/v1/catalogos/tipos-alojamiento")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sin token el catálogo de servicios responde 401")
    void catalogoSinTokenRecibe401() throws Exception {
        mvc.perform(get("/api/v1/catalogos/servicios")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El dueño consulta su publicación: responde 200 con los datos guardados")
    void duenoConsultaSuPublicacion() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = idCreado(token);

        consultar(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.intValue()))
                .andExpect(jsonPath("$.titulo").value("Casa en la playa"))
                .andExpect(jsonPath("$.capacidad").value(4));
    }

    @Test
    @DisplayName("Otro anfitrión o un huésped no pueden consultar un borrador ajeno: responden 403")
    void consultarBorradorAjenoRecibe403() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));

        consultar(iniciarSesion("marta@correo.com"), id).andExpect(status().isForbidden());
        consultar(iniciarSesion("ana@correo.com"), id).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El administrador consulta la publicación de un anfitrión: responde 200")
    void administradorConsultaCualquierPublicacion() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Admin LR", "admin@correo.com", codificador.encode(CONTRASENA),
                Rol.ADMINISTRADOR));

        consultar(iniciarSesion("admin@correo.com"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anfitrionId").value(anfitrion.getId().intValue()));
    }

    @Test
    @DisplayName("Consultar una publicación que no existe responde 404")
    void consultarInexistenteRecibe404() throws Exception {
        consultar(iniciarSesion("luis@correo.com"), 999_999L).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El dueño elimina su borrador con imágenes y servicios: responde 204 y ya no existe")
    void duenoEliminaSuBorrador() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        String creada = crear(token, """
                {"titulo": "Casa en la playa", "servicios": [%d],
                 "imagenes": ["https://imagenes.com/1.jpg"]}
                """.formatted(idServicio("Wifi")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(creada).get("id").asLong();

        eliminar(token, id).andExpect(status().isNoContent());

        assertThat(publicaciones.findById(id)).isEmpty();
        consultar(token, id).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Otro anfitrión no puede eliminar la publicación: responde 403 y sigue existiendo")
    void otroAnfitrionNoPuedeEliminar() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));

        eliminar(iniciarSesion("marta@correo.com"), id).andExpect(status().isForbidden());

        assertThat(publicaciones.findById(id)).isPresent();
    }

    @Test
    @DisplayName("El administrador elimina el borrador de un anfitrión: responde 204")
    void administradorEliminaCualquierBorrador() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Admin LR", "admin@correo.com", codificador.encode(CONTRASENA),
                Rol.ADMINISTRADOR));

        eliminar(iniciarSesion("admin@correo.com"), id).andExpect(status().isNoContent());

        assertThat(publicaciones.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Eliminar una publicación que no existe responde 404")
    void eliminarInexistenteRecibe404() throws Exception {
        eliminar(iniciarSesion("luis@correo.com"), 999_999L).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Editar sin cambiar las imágenes conserva sus filas: los ids no cambian")
    void editarConLasMismasImagenesConservaSusIds() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        String cuerpo = """
                {"titulo": "Casa en la playa",
                 "imagenes": ["https://imagenes.com/1.jpg", "https://imagenes.com/2.jpg"]}
                """;
        Long id = json.readTree(crear(token, cuerpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        List<Long> antes = idsDeImagenes(id);

        editar(token, id, cuerpo).andExpect(status().isOk());

        assertThat(idsDeImagenes(id)).isEqualTo(antes);
    }

    @Test
    @DisplayName("Al reordenar y quitar imágenes, las que siguen conservan su id y la primera es la principal")
    void reordenarYQuitarImagenesConservaLasQueSiguen() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = json.readTree(crear(token, """
                {"titulo": "Casa en la playa", "imagenes": ["https://imagenes.com/1.jpg",
                 "https://imagenes.com/2.jpg", "https://imagenes.com/3.jpg"]}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        List<Long> antes = idsDeImagenes(id);

        editar(token, id, """
                {"titulo": "Casa en la playa",
                 "imagenes": ["https://imagenes.com/3.jpg", "https://imagenes.com/1.jpg"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagenes[0]").value("https://imagenes.com/3.jpg"))
                .andExpect(jsonPath("$.imagenes[1]").value("https://imagenes.com/1.jpg"))
                .andExpect(jsonPath("$.imagenes.length()").value(2));

        // antes: [1.jpg, 2.jpg, 3.jpg]; ahora 3.jpg va primero y 2.jpg ya no está.
        assertThat(idsDeImagenes(id)).containsExactly(antes.get(2), antes.get(0));
    }

    @Test
    @DisplayName("Editar con los mismos servicios conserva sus filas; quitar uno solo borra esa fila")
    void editarServiciosConservaLasFilasQueSiguen() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long wifi = idServicio("Wifi");
        Long piscina = idServicio("Piscina");
        String cuerpo = """
                {"titulo": "Casa en la playa", "servicios": [%d, %d]}
                """.formatted(wifi, piscina);
        Long id = json.readTree(crear(token, cuerpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        Long filaWifi = idFilaServicio(id, wifi);
        List<Long> antes = idsDeServicios(id);

        editar(token, id, cuerpo).andExpect(status().isOk());
        assertThat(idsDeServicios(id)).isEqualTo(antes);

        editar(token, id, """
                {"titulo": "Casa en la playa", "servicios": [%d]}
                """.formatted(wifi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios.length()").value(1));
        assertThat(idsDeServicios(id)).containsExactly(filaWifi);
    }

    @Test
    @DisplayName("Con varios servicios y varias imágenes, el detalle no repite imágenes ni servicios")
    void consultarConVariosServiciosEImagenesNoDuplica() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = json.readTree(crear(token, """
                {"titulo": "Casa en la playa", "servicios": [%d, %d],
                 "imagenes": ["https://imagenes.com/1.jpg", "https://imagenes.com/2.jpg"]}
                """.formatted(idServicio("Wifi"), idServicio("Piscina")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        consultar(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios.length()").value(2))
                .andExpect(jsonPath("$.imagenes.length()").value(2))
                .andExpect(jsonPath("$.imagenes[0]").value("https://imagenes.com/1.jpg"))
                .andExpect(jsonPath("$.imagenes[1]").value("https://imagenes.com/2.jpg"));
    }

    @Test
    @DisplayName("PUT de servicios cambia solo los servicios: el título y las imágenes quedan igual")
    void putServiciosCambiaSoloLosServicios() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = json.readTree(crear(token, """
                {"titulo": "Casa en la playa", "servicios": [%d],
                 "imagenes": ["https://imagenes.com/1.jpg"]}
                """.formatted(idServicio("Wifi")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        Long cocina = idServicio("Cocina");

        mvc.perform(put(RUTA + "/" + id + "/servicios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"servicios": [%d]}
                                """.formatted(cocina)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios.length()").value(1))
                .andExpect(jsonPath("$.servicios[0].nombre").value("Cocina"))
                .andExpect(jsonPath("$.titulo").value("Casa en la playa"))
                .andExpect(jsonPath("$.imagenes[0]").value("https://imagenes.com/1.jpg"))
                .andExpect(jsonPath("$.editadoPor").value(anfitrion.getId().intValue()));
    }

    @Test
    @DisplayName("PUT de servicios de otro anfitrión responde 403 y sin lista responde 400")
    void putServiciosRechazaAjenoYSinLista() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));

        mvc.perform(put(RUTA + "/" + id + "/servicios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("marta@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servicios\": []}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(RUTA + "/" + id + "/servicios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("luis@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.servicios").value("La lista de servicios es obligatoria."));
    }

    @Test
    @DisplayName("PUT de imágenes reordena y quita sin tocar lo demás: la primera queda como principal")
    void putImagenesReordenaSinTocarLoDemas() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = json.readTree(crear(token, """
                {"titulo": "Casa en la playa", "imagenes": ["https://imagenes.com/1.jpg",
                 "https://imagenes.com/2.jpg", "https://imagenes.com/3.jpg"]}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        List<Long> antes = idsDeImagenes(id);

        mvc.perform(put(RUTA + "/" + id + "/imagenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"imagenes": ["https://imagenes.com/2.jpg", "https://imagenes.com/1.jpg"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagenes.length()").value(2))
                .andExpect(jsonPath("$.imagenes[0]").value("https://imagenes.com/2.jpg"))
                .andExpect(jsonPath("$.titulo").value("Casa en la playa"));

        assertThat(idsDeImagenes(id)).containsExactly(antes.get(1), antes.get(0));
    }

    @Test
    @DisplayName("PUT de imágenes de un huésped responde 403 y con una URL inválida responde 400")
    void putImagenesRechazaHuespedYUrlInvalida() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));

        mvc.perform(put(RUTA + "/" + id + "/imagenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imagenes\": []}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(RUTA + "/" + id + "/imagenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("luis@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imagenes\": [\"ftp://imagen.jpg\"]}"))
                .andExpect(status().isBadRequest());
    }

    private List<Long> idsDeServicios(Long publicacionId) {
        return jdbc.queryForList("SELECT id FROM publicacion_servicio WHERE publicacion_id = ? ORDER BY id",
                Long.class, publicacionId);
    }

    private Long idFilaServicio(Long publicacionId, Long servicioId) {
        return jdbc.queryForObject(
                "SELECT id FROM publicacion_servicio WHERE publicacion_id = ? AND servicio_id = ?",
                Long.class, publicacionId, servicioId);
    }

    private List<Long> idsDeImagenes(Long publicacionId) {
        return jdbc.queryForList("SELECT id FROM publicacion_imagen WHERE publicacion_id = ? ORDER BY posicion",
                Long.class, publicacionId);
    }

    private ResultActions consultar(String token, Long id) throws Exception {
        return mvc.perform(get(RUTA + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions eliminar(String token, Long id) throws Exception {
        return mvc.perform(delete(RUTA + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Long idServicio(String nombre) {
        return catalogo.findAllByOrderByNombreAsc().stream()
                .filter(servicio -> servicio.getNombre().equals(nombre))
                .findFirst().orElseThrow().getId();
    }

    private Long idTipo(String nombre) {
        return tipos.findAllByOrderByNombreAsc().stream()
                .filter(tipo -> tipo.getNombre().equals(nombre))
                .findFirst().orElseThrow().getId();
    }

    private Long idCreado(String token) throws Exception {
        String respuesta = crear(token, """
                {"titulo": "Casa en la playa", "tipo": %d, "capacidad": 4}
                """.formatted(idTipo("Casa")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private ResultActions editar(String token, Long id, String cuerpo) throws Exception {
        return mvc.perform(put(RUTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private String iniciarSesion(String correo) throws Exception {
        String cuerpo = """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, CONTRASENA);
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }

    private ResultActions crear(String token, String cuerpo) throws Exception {
        return mvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }
}
