package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.identidad.UsuariosDePrueba;
import co.edu.unbosque.marketplace.identidad.exceptions.UsuarioNoEncontradoException;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerfilServiceTest {

    private static final Long ID = 7L;
    private static final String TELEFONO = "+573001234567";
    private static final String FOTO = "https://fotos.ejemplo.com/ana.jpg";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PerfilService servicio = new PerfilService(usuarios);

    @Test
    @DisplayName("Consultar el perfil devuelve la cuenta del dueño")
    void consultaElPerfilPropio() {
        Usuario ana = UsuariosDePrueba.activa(ID);
        when(usuarios.findById(ID)).thenReturn(Optional.of(ana));

        assertThat(servicio.consultar(ID)).isSameAs(ana);
    }

    @Test
    @DisplayName("Actualizar el perfil guarda nombre, teléfono y foto, y conserva el correo")
    void actualizaYGuardaLosDatos() {
        Usuario ana = UsuariosDePrueba.activa(ID);
        when(usuarios.findById(ID)).thenReturn(Optional.of(ana));
        when(usuarios.save(ana)).thenReturn(ana);

        Usuario actualizado = servicio.actualizar(ID, "Ana María", TELEFONO, FOTO);

        assertThat(actualizado.getNombre()).isEqualTo("Ana María");
        assertThat(actualizado.getTelefono()).isEqualTo(TELEFONO);
        assertThat(actualizado.getFotoUrl()).isEqualTo(FOTO);
        assertThat(actualizado.getCorreo()).isEqualTo("ana@correo.com");
        verify(usuarios).save(ana);
    }

    @Test
    @DisplayName("Si la cuenta no existe, consultar y actualizar dan UsuarioNoEncontrado sin guardar nada")
    void usuarioInexistente() {
        when(usuarios.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ID)).isInstanceOf(UsuarioNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.actualizar(ID, "Ana", null, null))
                .isInstanceOf(UsuarioNoEncontradoException.class);

        verify(usuarios, never()).save(any());
    }
}
