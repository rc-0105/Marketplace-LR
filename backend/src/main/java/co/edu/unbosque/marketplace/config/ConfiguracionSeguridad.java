package co.edu.unbosque.marketplace.config;

import jakarta.servlet.DispatcherType;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de seguridad stateless. Cada endpoint de /api/v1 declara sus roles con
 * {@code @PreAuthorize} y lo demás se niega por defecto (ADR-5, REQ-A8).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    private static final String RUTA_REGISTRO = "/api/v1/auth/registro";
    private static final String RUTA_LOGIN = "/api/v1/auth/login";
    private static final String RUTA_REACTIVAR = "/api/v1/perfil/reactivar";
    private static final String RUTA_RECUPERACION = "/api/v1/auth/recuperacion";
    private static final String RUTA_CONFIRMAR_RECUPERACION = "/api/v1/auth/recuperacion/confirmar";
    // Rutas de quien aún no tiene sesión: públicas y sin leer el Bearer.
    private static final Set<String> RUTAS_DE_ACCESO = Set.of(RUTA_LOGIN, RUTA_REGISTRO, RUTA_REACTIVAR,
            RUTA_RECUPERACION, RUTA_CONFIRMAR_RECUPERACION);

    @Bean
    public SecurityFilterChain cadena(HttpSecurity http, ManejadorFallosSeguridad fallos,
            JwtAuthenticationConverter convertidor, BearerTokenResolver resolverToken,
            AutorizacionDeclarada autorizacionDeclarada) throws Exception {
        http
                // CSRF se deshabilita a propósito: la API es stateless (sin cookies ni sesión HTTP) y
                // autentica solo con el encabezado Authorization: Bearer, que el cliente agrega de forma
                // explícita y el navegador nunca envía por su cuenta; no hay credencial ambiente que explotar.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(rutas -> rutas
                        // Los errores internos que Boot reenvía a /error no se bloquean.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, RUTAS_DE_ACCESO.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/estado", "/actuator/health",
                                "/actuator/health/**", "/actuator/info", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // El rol lo verifica el @PreAuthorize del endpoint; sin él, se niega.
                        .requestMatchers("/api/v1/**").access(autorizacionDeclarada)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor))
                        .bearerTokenResolver(resolverToken)
                        .authenticationEntryPoint(fallos)
                        .accessDeniedHandler(fallos)
                        // El handler por defecto relanza AuthenticationServiceException (500):
                        // el propio manejador decide entre 503 (denylist caída) y 401 (D5).
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filtro) {
                                filtro.setAuthenticationFailureHandler(fallos);
                                return filtro;
                            }
                        }))
                // Los anónimos que caen en denyAll reciben el mismo 401 ProblemDetail.
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(fallos)
                        .accessDeniedHandler(fallos));
        return http.build();
    }

    /**
     * Registro, login, reactivación y recuperación ignoran el Bearer: un token vencido que el cliente aún
     * guarda no debe impedirle volver a entrar (D7).
     */
    @Bean
    public BearerTokenResolver resolverToken() {
        DefaultBearerTokenResolver porDefecto = new DefaultBearerTokenResolver();
        return peticion -> {
            boolean esAcceso = HttpMethod.POST.matches(peticion.getMethod())
                    && RUTAS_DE_ACCESO.contains(peticion.getRequestURI());
            return esAcceso ? null : porDefecto.resolve(peticion);
        };
    }
}
