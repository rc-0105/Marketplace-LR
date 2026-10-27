package co.edu.unbosque.marketplace.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.ObjectPostProcessor;
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
 * Cadena de seguridad stateless. Cada ruta declara quién entra y lo demás se
 * niega por defecto (ADR-5, REQ-A8).
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    private static final String RUTA_REGISTRO = "/api/v1/auth/registro";
    private static final String RUTA_LOGIN = "/api/v1/auth/login";
    private static final String RUTA_LOGOUT = "/api/v1/auth/logout";

    @Bean
    public SecurityFilterChain cadena(HttpSecurity http, ManejadorFallosSeguridad fallos,
            JwtAuthenticationConverter convertidor, BearerTokenResolver resolverToken) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(rutas -> rutas
                        // Los errores internos que Boot reenvía a /error no se bloquean.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, RUTA_REGISTRO, RUTA_LOGIN).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/estado", "/actuator/health",
                                "/actuator/health/**", "/actuator/info", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, RUTA_LOGOUT).authenticated()
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
     * Registro y login ignoran el Bearer: un token vencido que el cliente aún
     * guarda no debe impedirle volver a entrar (D7).
     */
    @Bean
    public BearerTokenResolver resolverToken() {
        DefaultBearerTokenResolver porDefecto = new DefaultBearerTokenResolver();
        return peticion -> {
            boolean esAcceso = HttpMethod.POST.matches(peticion.getMethod())
                    && (RUTA_LOGIN.equals(peticion.getRequestURI())
                    || RUTA_REGISTRO.equals(peticion.getRequestURI()));
            return esAcceso ? null : porDefecto.resolve(peticion);
        };
    }
}
