package co.edu.unbosque.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PropiedadesJwtTest {

    private static final String SECRETO_VALIDO = "Z".repeat(32);

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(Configuracion.class);

    @Configuration
    @EnableConfigurationProperties(PropiedadesJwt.class)
    static class Configuracion {
    }

    @Test
    @DisplayName("Si falta el secreto el contexto no arranca")
    void fallaSinSecreto() {
        contexto.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("Un secreto de 31 bytes se rechaza sin imprimir su valor")
    void rechazaSecretoCorto() {
        String corto = "Z".repeat(31);
        contexto.withPropertyValues("jwt.secreto=" + corto).run(ctx -> {
            assertThat(ctx).hasFailed();
            Throwable error = ctx.getStartupFailure();
            while (error != null) {
                assertThat(String.valueOf(error.getMessage())).doesNotContain(corto);
                error = error.getCause();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    @DisplayName("Una expiración no positiva se rechaza sin imprimir el secreto")
    void rechazaExpiracionNoPositiva(String minutos) {
        contexto.withPropertyValues("jwt.secreto=" + SECRETO_VALIDO, "jwt.expiracion-minutos=" + minutos)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    Throwable error = ctx.getStartupFailure();
                    boolean mencionaExpiracion = false;
                    while (error != null) {
                        String mensaje = String.valueOf(error.getMessage());
                        assertThat(mensaje).doesNotContain(SECRETO_VALIDO);
                        mencionaExpiracion |= mensaje.contains("expiracionMinutos");
                        error = error.getCause();
                    }
                    assertThat(mencionaExpiracion).isTrue();
                });
    }

    @Test
    @DisplayName("Un secreto de 32 bytes arranca y la expiración por defecto es 60 minutos")
    void arrancaConSecretoValido() {
        contexto.withPropertyValues("jwt.secreto=" + SECRETO_VALIDO).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            PropiedadesJwt propiedades = ctx.getBean(PropiedadesJwt.class);
            assertThat(propiedades.expiracionMinutos()).isEqualTo(60);
            assertThat(propiedades.toString()).doesNotContain(SECRETO_VALIDO);
        });
    }
}
