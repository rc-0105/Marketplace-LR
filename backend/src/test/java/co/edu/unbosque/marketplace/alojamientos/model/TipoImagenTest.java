package co.edu.unbosque.marketplace.alojamientos.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TipoImagenTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Test
    @DisplayName("Reconoce un PNG y un JPEG por sus primeros bytes")
    void reconocePngYJpeg() {
        assertThat(TipoImagen.detectar(PNG)).isEqualTo(TipoImagen.PNG);
        assertThat(TipoImagen.detectar(JPEG)).isEqualTo(TipoImagen.JPEG);
    }

    @Test
    @DisplayName("Un archivo que no es PNG ni JPEG no se reconoce, aunque el cliente diga lo contrario")
    void rechazaLoDemas() {
        assertThat(TipoImagen.detectar("<svg onload=alert(1)>".getBytes())).isNull();
        assertThat(TipoImagen.detectar("GIF89a......".getBytes())).isNull();
        assertThat(TipoImagen.detectar("%PDF-1.7".getBytes())).isNull();
    }

    @Test
    @DisplayName("Un contenido vacío, nulo o más corto que la firma no se reconoce")
    void rechazaVacioNuloOCorto() {
        assertThat(TipoImagen.detectar(new byte[0])).isNull();
        assertThat(TipoImagen.detectar(null)).isNull();
        assertThat(TipoImagen.detectar(new byte[] {(byte) 0x89, 'P'})).isNull();
        assertThat(TipoImagen.detectar(new byte[] {(byte) 0xFF, (byte) 0xD8})).isNull();
    }

    @Test
    @DisplayName("Cada formato se sirve con su tipo de medio")
    void tipoDeMedio() {
        assertThat(TipoImagen.PNG.tipoMedio().toString()).isEqualTo("image/png");
        assertThat(TipoImagen.JPEG.tipoMedio().toString()).isEqualTo("image/jpeg");
    }
}
