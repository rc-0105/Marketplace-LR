package co.edu.unbosque.marketplace.alojamientos.model;

import org.springframework.http.MediaType;

/**
 * Formatos de imagen que acepta la plataforma. Se reconocen por los primeros bytes del archivo y no por
 * el nombre ni por el tipo que declara el cliente, que cualquiera puede falsear.
 */
public enum TipoImagen {
    PNG(MediaType.IMAGE_PNG),
    JPEG(MediaType.IMAGE_JPEG);

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final MediaType tipoMedio;

    TipoImagen(MediaType tipoMedio) {
        this.tipoMedio = tipoMedio;
    }

    public MediaType tipoMedio() {
        return tipoMedio;
    }

    /** El formato del contenido, o {@code null} si no es PNG ni JPEG. */
    public static TipoImagen detectar(byte[] contenido) {
        if (empiezaCon(contenido, FIRMA_PNG)) {
            return PNG;
        }
        if (empiezaCon(contenido, FIRMA_JPEG)) {
            return JPEG;
        }
        return null;
    }

    private static boolean empiezaCon(byte[] contenido, byte[] firma) {
        if (contenido == null || contenido.length < firma.length) {
            return false;
        }
        for (int posicion = 0; posicion < firma.length; posicion++) {
            if (contenido[posicion] != firma[posicion]) {
                return false;
            }
        }
        return true;
    }
}
