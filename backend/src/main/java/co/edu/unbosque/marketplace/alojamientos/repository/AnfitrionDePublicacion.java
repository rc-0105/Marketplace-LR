package co.edu.unbosque.marketplace.alojamientos.repository;

/** Solo el dueño de una publicación: verificar la propiedad no necesita cargar la entidad. */
public interface AnfitrionDePublicacion {

    Long getAnfitrionId();
}
