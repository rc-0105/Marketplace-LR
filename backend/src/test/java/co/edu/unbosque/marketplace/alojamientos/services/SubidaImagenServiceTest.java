package co.edu.unbosque.marketplace.alojamientos.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.exceptions.ImagenNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.ImagenNoValidaException;
import co.edu.unbosque.marketplace.alojamientos.model.TipoImagen;
import co.edu.unbosque.marketplace.alojamientos.services.AlmacenImagenes.ImagenGuardada;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubidaImagenServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final AlmacenImagenes almacen = mock(AlmacenImagenes.class);
    private final SubidaImagenService servicio = new SubidaImagenService(publicaciones, almacen);

    @Test
    @DisplayName("Guarda una imagen válida del dueño y devuelve su URL pública")
    void guardaUnaImagenValida() {
        when(almacen.guardar(7L, PNG, TipoImagen.PNG)).thenReturn("/api/v1/imagenes/abc");

        assertThat(servicio.subir(7L, 3L, false, PNG)).isEqualTo("/api/v1/imagenes/abc");
        verify(publicaciones).consultar(7L, 3L, false);
    }

    @Test
    @DisplayName("Si el usuario no tiene acceso a la publicación no se revisa ni se guarda el archivo")
    void sinAccesoNoGuarda() {
        when(publicaciones.consultar(7L, 9L, false)).thenThrow(new AccesoDenegadoException());

        assertThatThrownBy(() -> servicio.subir(7L, 9L, false, PNG)).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(almacen);
    }

    @Test
    @DisplayName("Rechaza un archivo vacío")
    void rechazaVacio() {
        assertThatThrownBy(() -> servicio.subir(7L, 3L, false, new byte[0]))
                .isInstanceOf(ImagenNoValidaException.class).hasMessage("El archivo está vacío.");
        verifyNoInteractions(almacen);
    }

    @Test
    @DisplayName("Rechaza lo que no es PNG ni JPEG por su contenido")
    void rechazaOtroFormato() {
        assertThatThrownBy(() -> servicio.subir(7L, 3L, false, "<script>alert(1)</script>".getBytes()))
                .isInstanceOf(ImagenNoValidaException.class).hasMessage("Solo se admiten imágenes PNG o JPG.");
        verifyNoInteractions(almacen);
    }

    @Test
    @DisplayName("Rechaza una imagen de más de 5 MB, aunque sea un PNG válido")
    void rechazaLoMuyPesado() {
        byte[] grande = new byte[SubidaImagenService.TAMANO_MAXIMO_BYTES + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);

        assertThatThrownBy(() -> servicio.subir(7L, 3L, false, grande))
                .isInstanceOf(ImagenNoValidaException.class).hasMessage("La imagen pesa más de 5 MB.");
        verifyNoInteractions(almacen);
    }

    @Test
    @DisplayName("Acepta una imagen de exactamente 5 MB")
    void aceptaElLimiteExacto() {
        byte[] justa = new byte[SubidaImagenService.TAMANO_MAXIMO_BYTES];
        System.arraycopy(PNG, 0, justa, 0, PNG.length);
        when(almacen.guardar(any(), any(), any())).thenReturn("/api/v1/imagenes/xyz");

        assertThat(servicio.subir(7L, 3L, false, justa)).isEqualTo("/api/v1/imagenes/xyz");
    }

    @Test
    @DisplayName("Lee una imagen guardada y responde 404 si no existe")
    void leeOResponde404() {
        UUID existente = UUID.randomUUID();
        UUID ausente = UUID.randomUUID();
        ImagenGuardada guardada = new ImagenGuardada(TipoImagen.PNG, PNG);
        when(almacen.leer(existente)).thenReturn(Optional.of(guardada));
        when(almacen.leer(ausente)).thenReturn(Optional.empty());

        assertThat(servicio.obtener(existente)).isEqualTo(guardada);
        assertThatThrownBy(() -> servicio.obtener(ausente)).isInstanceOf(ImagenNoEncontradaException.class);
    }
}
