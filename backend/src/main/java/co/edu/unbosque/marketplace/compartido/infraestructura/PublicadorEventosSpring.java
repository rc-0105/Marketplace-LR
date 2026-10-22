package co.edu.unbosque.marketplace.compartido.infraestructura;

import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Adaptador que entrega los eventos al bus interno de Spring. */
@Component
public class PublicadorEventosSpring implements PublicadorEventos {

    private final ApplicationEventPublisher bus;

    public PublicadorEventosSpring(ApplicationEventPublisher bus) {
        this.bus = bus;
    }

    @Override
    public void publicar(EventoCritico evento) {
        bus.publishEvent(evento);
    }
}
