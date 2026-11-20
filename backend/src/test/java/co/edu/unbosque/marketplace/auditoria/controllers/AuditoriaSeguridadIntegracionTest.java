package co.edu.unbosque.marketplace.auditoria.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.auditoria.SesionesAuditoria;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

/** Solo el administrador consulta la auditoría, y la autorización se evalúa antes de validar (REQ-CA8, ADR-5). */
class AuditoriaSeguridadIntegracionTest extends AuditoriaHttpIntegracionBase {

    private static final List<String> RUTAS = List.of(RUTA, RUTA + "/1");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {RUTA, RUTA + "/1"})
    @DisplayName("Sin token responde 401")
    void sinTokenEs401(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SesionesAuditoria.HUESPED, SesionesAuditoria.ANFITRION})
    @DisplayName("Un huésped o un anfitrión recibe 403 en la lista y en el detalle")
    void otrosRolesSon403(String correo) throws Exception {
        String tokenDeOtroRol = sesiones.token(correo);

        for (String ruta : RUTAS) {
            consultar(ruta, tokenDeOtroRol).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("El administrador recibe 200 en la lista y en el detalle")
    void administradorEs200() throws Exception {
        semilla.sembrarBase();

        for (String ruta : RUTAS) {
            consultar(ruta).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Un huésped con tamano=0 recibe 403 y no 400: primero se autoriza")
    void autorizaAntesDeValidar() throws Exception {
        consultar(RUTA + "?tamano=0", sesiones.token(SesionesAuditoria.HUESPED)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cada método del controlador declara solo al administrador")
    void declaraAlAdministrador() {
        List<Method> metodos = Arrays.stream(AuditoriaController.class.getDeclaredMethods())
                .filter(metodo -> metodo.isAnnotationPresent(GetMapping.class))
                .toList();

        assertThat(metodos).hasSize(2).allSatisfy(metodo -> {
            PreAuthorize declaracion = AnnotatedElementUtils.findMergedAnnotation(metodo, PreAuthorize.class);
            if (declaracion == null) {
                declaracion = AnnotatedElementUtils.findMergedAnnotation(AuditoriaController.class,
                        PreAuthorize.class);
            }
            assertThat(declaracion).isNotNull();
            assertThat(declaracion.value()).isEqualTo(Permisos.ADMINISTRADOR);
        });
    }
}
