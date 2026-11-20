package co.edu.unbosque.marketplace.compartido.idempotencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.ClaveIdempotenciaInvalidaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/** Orquestación de la idempotencia con el registro simulado y un reloj fijo (SOF-77). */
class ServicioIdempotenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-20T15:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(120);
    private static final Duration VIGENCIA = Duration.ofHours(24);
    private static final String CLAVE = "3f2b8c1e-5a7d-4e6f-9b0a-1c2d3e4f5a6b";
    private static final String OPERACION = "POST /reservas";
    private static final String CONTENIDO = "10|2026-12-10|2026-12-13|7,8";

    private RegistroIdempotencia registro;
    private ServicioIdempotencia servicio;
    private final UUID reclamo = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        registro = mock(RegistroIdempotencia.class);
        servicio = servicioConReloj(Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private ServicioIdempotencia servicioConReloj(Clock reloj) {
        return new ServicioIdempotencia(registro, reloj, new ObjectMapper(), 24, 120, 2);
    }

    private static SolicitudIdempotente solicitud() {
        return new SolicitudIdempotente(2L, CLAVE, OPERACION, CONTENIDO);
    }

    @Test
    @DisplayName("Primera vez: reclama, ejecuta una vez, serializa y completa con 201 y el cuerpo")
    void primeraVez() {
        UUID clave = UUID.fromString(CLAVE);
        when(registro.reclamar(eq(2L), eq(clave), anyString(), eq(AHORA), eq(LEASE))).thenReturn(Optional.of(reclamo));
        when(registro.completar(eq(2L), eq(clave), eq(reclamo), anyInt(), anyString(), eq(VIGENCIA)))
                .thenReturn(true);
        AtomicInteger ejecuciones = new AtomicInteger();

        RespuestaIdempotente respuesta = servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> {
            ejecuciones.incrementAndGet();
            return Map.of("id", 7);
        });

        assertThat(ejecuciones).hasValue(1);
        assertThat(respuesta.codigoEstado()).isEqualTo(201);
        assertThat(respuesta.cuerpo()).isEqualTo("{\"id\":7}");
        assertThat(respuesta.replicada()).isFalse();
        verify(registro).completar(2L, clave, reclamo, 201, "{\"id\":7}", VIGENCIA);
    }

    @Test
    @DisplayName("La respuesta se entrega como application/json en UTF-8 y sin marca de réplica la primera vez")
    void respuestaOriginalSinMarca() {
        RespuestaIdempotente original = new RespuestaIdempotente(201, "{\"a\":\"ñ\"}", false);

        var http = original.comoRespuesta();

        assertThat(http.getStatusCode().value()).isEqualTo(201);
        assertThat(http.getHeaders().getContentType().toString()).isEqualToIgnoringCase("application/json;charset=UTF-8");
        assertThat(http.getHeaders()).doesNotContainKey("Idempotent-Replayed");
        assertThat(http.getBody()).isEqualTo("{\"a\":\"ñ\"}");
    }

    @Test
    @DisplayName("Una réplica lleva la cabecera Idempotent-Replayed: true")
    void respuestaReplicadaConMarca() {
        var http = new RespuestaIdempotente(201, "{}", true).comoRespuesta();

        assertThat(http.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "123", "12345678-1234-1234-1234-12345678901", " ",
            "3f2b8c1e-5a7d-4e6f-9b0a-1c2d3e4f5a6bX", "3f2b8c1e5a7d4e6f9b0a1c2d3e4f5a6b",
            "zzzzzzzz-5a7d-4e6f-9b0a-1c2d3e4f5a6b"})
    @DisplayName("Una clave que no es un UUID canónico de 36 caracteres se rechaza sin tocar el registro")
    void claveInvalida(String clave) {
        SolicitudIdempotente invalida = new SolicitudIdempotente(2L, clave, OPERACION, CONTENIDO);

        assertThatThrownBy(() -> servicio.ejecutar(invalida, HttpStatus.CREATED, () -> "x"))
                .isInstanceOf(ClaveIdempotenciaInvalidaException.class);
        verifyNoMoreInteractions(registro);
    }

    @Test
    @DisplayName("El UUID se acepta sin distinguir mayúsculas")
    void claveEnMayusculas() {
        when(registro.reclamar(any(), any(), anyString(), any(), any())).thenReturn(Optional.of(reclamo));
        when(registro.completar(any(), any(), any(), anyInt(), anyString(), any())).thenReturn(true);
        SolicitudIdempotente mayusculas = new SolicitudIdempotente(2L, CLAVE.toUpperCase(), OPERACION, CONTENIDO);

        RespuestaIdempotente respuesta = servicio.ejecutar(mayusculas, HttpStatus.CREATED, () -> Map.of("ok", true));

        assertThat(respuesta.codigoEstado()).isEqualTo(201);
        verify(registro, never()).liberar(any(), any(), any());
    }

    @Test
    @DisplayName("La serialización conserva el orden de inserción de las propiedades")
    void serializaConElMapperDeSpring() {
        when(registro.reclamar(any(), any(), anyString(), any(), any())).thenReturn(Optional.of(reclamo));
        when(registro.completar(any(), any(), any(), anyInt(), anyString(), any())).thenReturn(true);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("zeta", 1);
        cuerpo.put("alfa", 2);

        RespuestaIdempotente respuesta = servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> cuerpo);

        assertThat(respuesta.cuerpo()).isEqualTo("{\"zeta\":1,\"alfa\":2}");
        verify(registro, times(1)).reclamar(any(), any(), anyString(), any(), any());
    }
}
