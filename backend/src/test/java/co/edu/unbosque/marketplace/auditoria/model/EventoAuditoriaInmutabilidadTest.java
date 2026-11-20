package co.edu.unbosque.marketplace.auditoria.model;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.auditoria.repository.EventoAuditoriaRepository;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La API en código no ofrece forma de modificar ni borrar un evento (S-RA18, REQ-RA7). */
class EventoAuditoriaInmutabilidadTest {

    @Test
    @DisplayName("La entidad es inmutable y no tiene setters públicos")
    void entidadSinSetters() {
        List<String> setters = Arrays.stream(EventoAuditoria.class.getDeclaredMethods())
                .filter(metodo -> Modifier.isPublic(metodo.getModifiers()))
                .map(Method::getName)
                .filter(nombre -> nombre.startsWith("set"))
                .toList();

        assertThat(EventoAuditoria.class.isAnnotationPresent(Immutable.class)).isTrue();
        assertThat(setters).isEmpty();
    }

    @Test
    @DisplayName("El repositorio solo declara guardar, buscar por id y listar con especificación")
    void repositorioSoloLectura() {
        List<String> nombres = Arrays.stream(EventoAuditoriaRepository.class.getMethods())
                .map(Method::getName)
                .toList();

        assertThat(nombres).containsExactlyInAnyOrder("save", "findById", "findAll");
    }
}
