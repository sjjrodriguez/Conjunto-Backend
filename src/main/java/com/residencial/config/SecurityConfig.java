package com.residencial.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.residencial.dto.MensajeError;
import com.residencial.security.TokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import java.io.IOException;

/**
 * Reglas de acceso por ruta y rol. La comprobación "solo su apartamento"
 * se hace en los servicios con AccesoService.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final boolean enforce;
    private final ObjectMapper objectMapper;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public SecurityConfig(@Value("${app.security.enforce:false}") boolean enforce, ObjectMapper objectMapper) {
        this.enforce = enforce;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        AuthenticationEntryPoint noAutenticado =
                (req, res, ex) -> escribirError(res, 401, "Debes iniciar sesión para continuar");
        AccessDeniedHandler sinPermiso =
                (req, res, ex) -> escribirError(res, 403, "No tienes permiso para realizar esta acción");

        http
            .csrf(AbstractHttpConfigurer::disable) // API sin cookies: el token va en la cabecera
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(noAutenticado).accessDeniedHandler(sinPermiso))
            .oauth2ResourceServer(o -> o
                .bearerTokenResolver(bearerTokenResolver())
                .authenticationEntryPoint(noAutenticado)
                .accessDeniedHandler(sinPermiso)
                .jwt(j -> j.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                // Estado del servicio para Render (sin detalles); el resto de actuator no está expuesto
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                // Solo responden si SWAGGER_ENABLED=true
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                // Apartamentos
                .requestMatchers(HttpMethod.GET, "/api/apartamentos/*").access(autenticado())
                .requestMatchers("/api/apartamentos", "/api/apartamentos/**").access(admin())

                // Residentes
                .requestMatchers("/api/residentes/**").access(admin())

                // Vehículos (el propietario solo los suyos)
                .requestMatchers("/api/vehiculos/**").access(autenticado())

                // Obras
                .requestMatchers(HttpMethod.GET, "/api/obras", "/api/obras/en-proceso").access(admin())
                .requestMatchers(HttpMethod.PUT, "/api/obras/*/estado").access(admin())
                .requestMatchers(HttpMethod.DELETE, "/api/obras/*").access(admin())
                .requestMatchers("/api/obras", "/api/obras/**").access(autenticado())

                // Personal de obra
                .requestMatchers(HttpMethod.GET, "/api/personal-obra").access(admin())
                .requestMatchers(HttpMethod.DELETE, "/api/personal-obra/*").access(admin())
                .requestMatchers("/api/personal-obra", "/api/personal-obra/**").access(autenticado())

                // Zonas comunes
                .requestMatchers(HttpMethod.GET, "/api/zonas", "/api/zonas/**").access(autenticado())
                .requestMatchers("/api/zonas", "/api/zonas/**").access(admin())

                // Reservas
                .requestMatchers(HttpMethod.GET, "/api/reservas").access(admin())
                .requestMatchers(HttpMethod.PUT, "/api/reservas/*/estado").access(admin())
                .requestMatchers(HttpMethod.DELETE, "/api/reservas/*").access(admin())
                .requestMatchers("/api/reservas", "/api/reservas/**").access(autenticado())

                // Pagos
                .requestMatchers(HttpMethod.GET, "/api/pagos/apartamento/*").access(autenticado())
                .requestMatchers(HttpMethod.PUT, "/api/pagos/apartamento/*/simular").access(autenticado())
                .requestMatchers("/api/pagos", "/api/pagos/**").access(admin())

                // Notificaciones
                .requestMatchers("/api/notificaciones", "/api/notificaciones/**").access(admin())
                // Celulares que reciben avisos: cualquiera con sesión (DispositivoServiceImpl la exige).
                // La baja no pide sesión: se hace al cerrarla y basta con el token, que solo tiene ese celular
                .requestMatchers(HttpMethod.POST, "/api/dispositivos/baja").permitAll()
                .requestMatchers("/api/dispositivos", "/api/dispositivos/**").access(autenticado())

                .anyRequest().access(autenticado()));

        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> admin() {
        return regla(AuthorityAuthorizationManager.hasRole(TokenService.ROL_ADMIN));
    }

    private AuthorizationManager<RequestAuthorizationContext> autenticado() {
        return regla(AuthenticatedAuthorizationManager.authenticated());
    }

    /**
     * Con SECURITY_ENFORCE=false (modo transición) las peticiones sin token pasan,
     * para no romper la app antigua. Si llega un token, la regla se aplica igual.
     */
    private AuthorizationManager<RequestAuthorizationContext> regla(
            AuthorizationManager<RequestAuthorizationContext> manager) {
        if (enforce) return manager;
        return (auth, ctx) -> trustResolver.isAnonymous(auth.get())
                ? new AuthorizationDecision(true)
                : manager.check(auth, ctx);
    }

    private BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        // El login nunca necesita token: se ignora aunque llegue uno caducado. Las demás rutas de
        // /api/auth (cambiar la contraseña) sí lo usan
        return req -> req.getRequestURI().startsWith("/api/auth/") && req.getRequestURI().endsWith("/login")
                ? null : resolver.resolve(req);
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenService.CLAIM_ROL);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    private void escribirError(HttpServletResponse res, int status, String mensaje) throws IOException {
        res.setStatus(status);
        res.setCharacterEncoding("UTF-8");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getOutputStream(), new MensajeError(mensaje));
    }
}
