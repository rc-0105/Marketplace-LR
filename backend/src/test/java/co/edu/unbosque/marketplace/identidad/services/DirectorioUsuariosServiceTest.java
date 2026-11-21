package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Accesor mínimo del nombre del huésped para otros módulos (SOF-36, D4/D5). */
class DirectorioUsuariosServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final DirectorioUsuariosService servicio = new DirectorioUsuariosService(usuarios);

    @Test
    @DisplayName("Sin ids, no consulta la base y devuelve un mapa vacío (D7)")
    void sinIdsDevuelveMapaVacio() {
        Map<Long, HuespedBasico> resultado = servicio.consultarBasicos(Set.of());

        assertThat(resultado).isEmpty();
        verify(usuarios, never()).findBasicosByIdIn(any());
    }

    @Test
    @DisplayName("Con ids, devuelve un mapa de id a nombre básico")
    void conIdsDevuelveElMapaDeBasicos() {
        when(usuarios.findBasicosByIdIn(Set.of(7L, 8L)))
                .thenReturn(List.of(new HuespedBasico(7L, "Ana Gómez"), new HuespedBasico(8L, "Carla Ruiz")));

        Map<Long, HuespedBasico> resultado = servicio.consultarBasicos(Set.of(7L, 8L));

        assertThat(resultado).containsEntry(7L, new HuespedBasico(7L, "Ana Gómez"))
                .containsEntry(8L, new HuespedBasico(8L, "Carla Ruiz"));
    }

    @Test
    @DisplayName("Un id que ya no existe simplemente no aparece en el resultado")
    void unIdInexistenteNoAparece() {
        when(usuarios.findBasicosByIdIn(Set.of(7L, 99L)))
                .thenReturn(List.of(new HuespedBasico(7L, "Ana Gómez")));

        Map<Long, HuespedBasico> resultado = servicio.consultarBasicos(Set.of(7L, 99L));

        assertThat(resultado).hasSize(1).containsKey(7L);
    }
}
