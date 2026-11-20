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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.ClaveIdempotenciaInvalidaException;
import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.ClaveIdempotenciaReutilizadaException;
import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.SolicitudEnProcesoException;
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
import org.slf4j.LoggerFactory;
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

    private static final UUID CLAVE_UUID = UUID.fromString(CLAVE);

    private String huellaEsperada() {
        return ServicioIdempotencia.huella(OPERACION, CONTENIDO);
    }

    private RegistroClave completada(String huella, Instant venceEn) {
        return new RegistroClave(RegistroClave.Estado.COMPLETADA, huella, 201, "{\"id\":7}", AHORA.minusSeconds(10),
                venceEn);
    }

    private void claveTomada() {
        when(registro.reclamar(any(), any(), anyString(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Réplica: con la clave ya completada y la misma huella no ejecuta y devuelve lo guardado")
    void replica() {
        claveTomada();
        when(registro.buscar(2L, CLAVE_UUID))
                .thenReturn(Optional.of(completada(huellaEsperada(), AHORA.plus(VIGENCIA))));
        AtomicInteger ejecuciones = new AtomicInteger();

        RespuestaIdempotente respuesta = servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> {
            ejecuciones.incrementAndGet();
            return "no debe ejecutarse";
        });

        assertThat(ejecuciones).hasValue(0);
        assertThat(respuesta).isEqualTo(new RespuestaIdempotente(201, "{\"id\":7}", true));
        verify(registro, never()).completar(any(), any(), any(), anyInt(), anyString(), any());
        verify(registro, never()).liberar(any(), any(), any());
    }

    @Test
    @DisplayName("Cinco llamadas idénticas devuelven cinco respuestas iguales sin ejecutar nada")
    void replicaRepetida() {
        claveTomada();
        when(registro.buscar(2L, CLAVE_UUID))
                .thenReturn(Optional.of(completada(huellaEsperada(), AHORA.plus(VIGENCIA))));

        for (int i = 0; i < 5; i++) {
            RespuestaIdempotente respuesta = servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> "x");
            assertThat(respuesta.cuerpo()).isEqualTo("{\"id\":7}");
            assertThat(respuesta.replicada()).isTrue();
        }
    }

    @Test
    @DisplayName("Un segundo antes de vencer todavía replica")
    void replicaEnElLimite() {
        claveTomada();
        when(registro.buscar(2L, CLAVE_UUID))
                .thenReturn(Optional.of(completada(huellaEsperada(), AHORA.plusSeconds(1))));

        assertThat(servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> "x").replicada()).isTrue();
    }

    @Test
    @DisplayName("Si la fila se liberó entre el reclamo y la lectura, reintenta el reclamo una vez")
    void reintentaSiSeLiberoEnMedio() {
        when(registro.reclamar(any(), any(), anyString(), any(), any()))
                .thenReturn(Optional.empty(), Optional.of(reclamo));
        when(registro.buscar(2L, CLAVE_UUID)).thenReturn(Optional.empty());
        when(registro.completar(any(), any(), any(), anyInt(), anyString(), any())).thenReturn(true);

        RespuestaIdempotente respuesta = servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> Map.of("id", 9));

        assertThat(respuesta.replicada()).isFalse();
        assertThat(respuesta.cuerpo()).isEqualTo("{\"id\":9}");
        verify(registro, times(2)).reclamar(any(), any(), anyString(), any(), any());
    }

    private RegistroClave enProceso(String huella) {
        return new RegistroClave(RegistroClave.Estado.EN_PROCESO, huella, null, null, AHORA.minusSeconds(10),
                AHORA.plusSeconds(110));
    }

    @Test
    @DisplayName("Huella distinta sobre una fila COMPLETADA: 422, sin ejecutar ni tocar la fila")
    void huellaDistintaCompletada() {
        claveTomada();
        when(registro.buscar(2L, CLAVE_UUID))
                .thenReturn(Optional.of(completada("f".repeat(64), AHORA.plus(VIGENCIA))));
        AtomicInteger ejecuciones = new AtomicInteger();

        assertThatThrownBy(() -> servicio.ejecutar(solicitud(), HttpStatus.CREATED, ejecuciones::incrementAndGet))
                .isInstanceOf(ClaveIdempotenciaReutilizadaException.class);

        assertThat(ejecuciones).hasValue(0);
        verify(registro, never()).completar(any(), any(), any(), anyInt(), anyString(), any());
        verify(registro, never()).liberar(any(), any(), any());
    }

    @Test
    @DisplayName("Huella distinta sobre una fila EN_PROCESO: 422 y no 409 (la huella se evalúa primero)")
    void huellaDistintaEnProceso() {
        claveTomada();
        when(registro.buscar(2L, CLAVE_UUID)).thenReturn(Optional.of(enProceso("f".repeat(64))));

        assertThatThrownBy(() -> servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> "x"))
                .isInstanceOf(ClaveIdempotenciaReutilizadaException.class);
    }

    @Test
    @DisplayName("Misma huella con la fila EN_PROCESO: 409 con los segundos de reintento, sin ejecutar")
    void enProceso() {
        claveTomada();
        when(registro.buscar(2L, CLAVE_UUID)).thenReturn(Optional.of(enProceso(huellaEsperada())));
        AtomicInteger ejecuciones = new AtomicInteger();

        assertThatThrownBy(() -> servicio.ejecutar(solicitud(), HttpStatus.CREATED, ejecuciones::incrementAndGet))
                .isInstanceOfSatisfying(SolicitudEnProcesoException.class,
                        error -> assertThat(error.getSegundosReintento()).isEqualTo(2));
        assertThat(ejecuciones).hasValue(0);
    }

    @Test
    @DisplayName("Si tras reintentar sigue sin fila ni reclamo, responde 409 en proceso")
    void dosVueltasSinExito() {
        claveTomada();
        when(registro.buscar(2L, CLAVE_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> "x"))
                .isInstanceOf(SolicitudEnProcesoException.class);
        verify(registro, times(2)).reclamar(any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Otra clave del mismo usuario y la misma clave de otro usuario se reclaman como primera vez")
    void alcancePorUsuarioYClave() {
        when(registro.reclamar(any(), any(), anyString(), any(), any())).thenReturn(Optional.of(reclamo));
        when(registro.completar(any(), any(), any(), anyInt(), anyString(), any())).thenReturn(true);
        String otraClave = "11111111-2222-4333-8444-555555555555";

        servicio.ejecutar(new SolicitudIdempotente(2L, otraClave, OPERACION, CONTENIDO), HttpStatus.CREATED,
                () -> "a");
        servicio.ejecutar(new SolicitudIdempotente(4L, CLAVE, OPERACION, CONTENIDO), HttpStatus.CREATED, () -> "b");

        verify(registro).reclamar(eq(2L), eq(UUID.fromString(otraClave)), anyString(), any(), any());
        verify(registro).reclamar(eq(4L), eq(CLAVE_UUID), anyString(), any(), any());
        verify(registro, never()).buscar(any(), any());
    }

    private void reclamoOtorgado() {
        when(registro.reclamar(any(), any(), anyString(), any(), any())).thenReturn(Optional.of(reclamo));
    }

    @Test
    @DisplayName("Si la operación lanza una excepción de negocio libera el reclamo y relanza la misma instancia")
    void liberaAnteExcepcionDeNegocio() {
        reclamoOtorgado();
        SolicitudEnProcesoException fallo = new SolicitudEnProcesoException(3);

        assertThatThrownBy(() -> servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> {
            throw fallo;
        })).isSameAs(fallo);

        verify(registro).liberar(2L, CLAVE_UUID, reclamo);
        verify(registro, never()).completar(any(), any(), any(), anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("Ante una RuntimeException inesperada libera el reclamo y la propaga intacta")
    void liberaAnteExcepcionInesperada() {
        reclamoOtorgado();
        IllegalStateException fallo = new IllegalStateException("falló");

        assertThatThrownBy(() -> servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> {
            throw fallo;
        })).isSameAs(fallo);

        verify(registro).liberar(2L, CLAVE_UUID, reclamo);
    }

    @Test
    @DisplayName("Si liberar también falla, se relanza el error original de la operación")
    void falloAlLiberarNoOcultaElOriginal() {
        reclamoOtorgado();
        when(registro.liberar(any(), any(), any())).thenThrow(new IllegalStateException("base caída"));
        IllegalArgumentException original = new IllegalArgumentException("original");

        assertThatThrownBy(() -> servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> {
            throw original;
        })).isSameAs(original);
    }

    @Test
    @DisplayName("Un código de éxito que no es 2xx se rechaza antes de reclamar")
    void exitoNo2xx() {
        assertThatThrownBy(() -> servicio.ejecutar(solicitud(), HttpStatus.BAD_REQUEST, () -> "x"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoMoreInteractions(registro);
    }

    @Test
    @DisplayName("Si completar lanza, registra ERROR y devuelve el resultado exitoso sin repetir la operación")
    void completarLanza() {
        reclamoOtorgado();
        when(registro.completar(any(), any(), any(), anyInt(), anyString(), any()))
                .thenThrow(new IllegalStateException("base caída"));
        AtomicInteger ejecuciones = new AtomicInteger();

        ListAppender<ILoggingEvent> log = capturarLog();
        RespuestaIdempotente respuesta = servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> {
            ejecuciones.incrementAndGet();
            return Map.of("id", 7);
        });

        assertThat(respuesta.codigoEstado()).isEqualTo(201);
        assertThat(respuesta.cuerpo()).isEqualTo("{\"id\":7}");
        assertThat(ejecuciones).hasValue(1);
        assertThat(log.list).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
        verify(registro, never()).liberar(any(), any(), any());
    }

    @Test
    @DisplayName("Si completar devuelve false (reclamo perdido) registra ERROR y entrega el éxito")
    void completarDevuelveFalse() {
        reclamoOtorgado();
        when(registro.completar(any(), any(), any(), anyInt(), anyString(), any())).thenReturn(false);

        ListAppender<ILoggingEvent> log = capturarLog();
        RespuestaIdempotente respuesta = servicio.ejecutar(solicitud(), HttpStatus.CREATED, () -> Map.of("id", 7));

        assertThat(respuesta.replicada()).isFalse();
        assertThat(log.list).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
    }

    private static ListAppender<ILoggingEvent> capturarLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(ServicioIdempotencia.class);
        ListAppender<ILoggingEvent> eventos = new ListAppender<>();
        eventos.start();
        logger.addAppender(eventos);
        return eventos;
    }
}
