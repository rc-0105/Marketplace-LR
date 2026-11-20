 package co.edu.unbosque.marketplace.alojamientos.services;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.mock;
  import static org.mockito.Mockito.never;
  import static org.mockito.Mockito.verify;
  import static org.mockito.Mockito.when;

  import co.edu.unbosque.marketplace.alojamientos.exceptions.CambioEstadoNoPermitidoException;
  import co.edu.unbosque.marketplace.alojamientos.exceptions.PoliticaCancelacionNoValidaException;
  import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionIncompletaException;
  import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEncontradaException;
  import co.edu.unbosque.marketplace.alojamientos.exceptions.ServicioAdicionalRepetidoException;
  import co.edu.unbosque.marketplace.alojamientos.exceptions.ServicioNoValidoException;
  import co.edu.unbosque.marketplace.alojamientos.exceptions.TipoAlojamientoNoValidoException;
  import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
  import co.edu.unbosque.marketplace.alojamientos.model.EstadoPublicacion;
  import co.edu.unbosque.marketplace.alojamientos.model.ModoReserva;
  import co.edu.unbosque.marketplace.alojamientos.model.PoliticaCancelacion;
  import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
  import co.edu.unbosque.marketplace.alojamientos.model.ServicioAdicional;
  import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
  import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
  import co.edu.unbosque.marketplace.alojamientos.repository.PoliticaCancelacionRepository;
  <<<<<<< feature/SOF-30-detalle-publicacion
  =======
  import co.edu.unbosque.marketplace.alojamientos.repository.AccesoDePublicacion;
  >>>>>>> main
  import co.edu.unbosque.marketplace.alojamientos.repository.AnfitrionDePublicacion;
  import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
  import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
  import co.edu.unbosque.marketplace.alojamientos.repository.TipoAlojamientoRepository;
  import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
  import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
  import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
  import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
  import co.edu.unbosque.marketplace.compartido.excep
  ──── (906 lines hidden) ─────────────────────────────────────────────────────────────────────────────────────────────
  eption.class);
      }

      private static SolicitudConfiguracion.ServicioAdicional adicional(String nombre, String costo) {
          return new SolicitudConfiguracion.ServicioAdicional(nombre, new BigDecimal(costo));
      }

      private static Publicacion publicacionDe(Long anfitrionId) {
          return Publicacion.borrador(anfitrionId, new DatosBorrador("Casa en la playa", null, "Santa Marta", null,
                  CASA, 4, null, Set.of(PISCINA), null, List.of("https://imagenes.com/1.jpg")));
      }

      private static Publicacion publicacionCompletaDe(Long anfitrionId) {
          return Publicacion.borrador(anfitrionId, new DatosBorrador("Casa en la playa", "Frente al mar",
                  "Santa Marta", "Calle 1 # 2-3", CASA, 4, new BigDecimal("250000"), Set.of(PISCINA),
                  "No se admiten fiestas", List.of("https://imagenes.com/1.jpg")));
      }

      // ACTIVA solo se alcanza con la aprobación del administrador (SOF-23); aquí se fija directo.
      private static Publicacion publicacionEn(EstadoPublicacion estado) {
          Publicacion publicacion = publicacionCompletaDe(ANFITRION_ID);
          ReflectionTestUtils.setField(publicacion, "estado", estado);
          return publicacion;
      }

      private static Publicacion pendienteDesde(Long id, String enviadaEn, String creadaEn) {
          Publicacion publicacion = publicacionEn(EstadoPublicacion.PENDIENTE_REVISION);
          ReflectionTestUtils.setField(publicacion, "id", id);
          ReflectionTestUtils.setField(publicacion, "creadoEn", Instant.parse(creadaEn));
          ReflectionTestUtils.setField(publicacion, "enviadaARevisionEn", enviadaEn == null ? null : Instant.parse(enviadaEn));
          return publicacion;
      }

      private static SolicitudBorrador soloTitulo(String titulo) {
          return new SolicitudBorrador(titulo, null, null, null, null, null, null, null, null, null);
      }

      private EventoCritico eventoPublicado() {
          ArgumentCaptor<EventoCritico> captor = ArgumentCaptor.forClass(EventoCritico.class);
          verify(publicador).publicar(captor.capture());
          return captor.getValue();
      }

      private static AccesoDePublicacion accesoDe(Long anfitrionId, EstadoPublicacion estado) {
          return new AccesoDePublicacion() {
              @Override
              public Long getAnfitrionId() {
                  return anfitrionId;
              }

              @Override
              public EstadoPublicacion getEstado() {
                  return estado;
              }
          };
      }
  }

Este archivo son más de 600 líneas y solo hay dos conflictos, así que te paso las dos zonas resueltas. Si preferís el archivo completo, decime y te lo pego.

Conflicto 1 (imports): se queda el de main, que trae AccesoDePublicacion.

import co.edu.unbosque.marketplace.alojamientos.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.AccesoDePublicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.AnfitrionDePublicacion;

Conflicto 2 (tests de candidatos y detalle): se mezclan. El primer test lleva el anfitrionId de main, y los tests de consultarActiva son de SOF-30.

        List<CandidatoBusqueda> candidatos = servicio.buscarCandidatos("Santa Marta");

        assertThat(candidatos).singleElement().satisfies(c -> {
            assertThat(c.titulo()).isEqualTo("Casa en la playa");
            assertThat(c.anfitrionId()).isEqualTo(ANFITRION_ID);
            assertThat(c.ciudad()).isEqualTo("Santa Marta");
            assertThat(c.tipo()).isEqualTo("Casa");
            assertThat(c.capacidad()).isEqualTo(4);
            assertThat(c.precioNoche()).isEqualByComparingTo("250000");
            assertThat(c.imagenPrincipal()).isEqualTo("https://imagenes.com/1.jpg");
        });
    }

    @Test
    @DisplayName("El detalle público de una ACTIVA trae lo que ve el huésped")
    void consultarActivaDevuelveElDetalle() {
        when(publicaciones.findById(PUBLICACION_ID))
                .thenReturn(Optional.of(publicacionEn(EstadoPublicacion.ACTIVA)));

        DetalleAlojamiento detalle = servicio.consultarActiva(PUBLICACION_ID);

        assertThat(detalle.titulo()).isEqualTo("Casa en la playa");
        assertThat(detalle.ciudad()).isEqualTo("Santa Marta");
        assertThat(detalle.tipo()).isEqualTo("Casa");
        assertThat(detalle.capacidad()).isEqualTo(4);
        assertThat(detalle.precioNoche()).isEqualByComparingTo("250000");
        assertThat(detalle.imagenes()).containsExactly("https://imagenes.com/1.jpg");
    }

    @Test
    @DisplayName("El detalle público responde 404 si la publicación no existe o no está ACTIVA")
    void consultarActivaRechazaLasQueNoEstanActivas() {
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.consultarActiva(PUBLICACION_ID))
                .isInstanceOf(PublicacionNoEncontradaException.class);

        for (EstadoPublicacion estado : EstadoPublicacion.values()) {
            if (estado == EstadoPublicacion.ACTIVA) {
                continue;
            }
            when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(publicacionEn(estado)));
            assertThatThrownBy(() -> servicio.consultarActiva(PUBLICACION_ID))
                    .isInstanceOf(PublicacionNoEncontradaException.class);
        }
    }