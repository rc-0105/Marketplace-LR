package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** La referencia a una imagen: una URL http o https, o la ruta de una imagen subida a la plataforma. */
@Documented
@Constraint(validatedBy = UrlDeImagenValidator.class)
@Target({ElementType.TYPE_USE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlDeImagen {

    String message() default "Cada imagen debe ser una URL http o https, o una imagen subida.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
