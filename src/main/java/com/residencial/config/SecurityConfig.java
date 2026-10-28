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

    public SecurityConfig(@Value("${app.security.enforce:true}") boolean enforce, ObjectMapper objectMapper) {
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
                // Cerrar sesión necesita la sesión que se cierra; el resto de /api/auth (login, registro) es público
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").access(sesion())
                .requestMatchers(HttpMethod.PUT, "/api/auth/contrasena").access(sesion())
                // P6: activar y quitar la huella piden sesión; entrar con ella (biometria/login) es público
                .requestMatchers(HttpMethod.POST, "/api/auth/biometria/activar").access(sesion())
                .requestMatchers(HttpMethod.DELETE, "/api/auth/biometria").access(sesion())
                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                // P6: textos legales públicos; consentimientos y solicitudes de datos, de cualquier sesión
                .requestMatchers(HttpMethod.GET, "/api/legal/textos").permitAll()
                .requestMatchers("/api/legal/**").access(sesion())
                .requestMatchers(HttpMethod.POST, "/api/solicitudes-datos").access(sesion())
                .requestMatchers(HttpMethod.GET, "/api/solicitudes-datos/mias").access(sesion())
                .requestMatchers("/api/solicitudes-datos", "/api/solicitudes-datos/**").access(admin())
                // Estado del servicio para Render (sin detalles); el resto de actuator no está expuesto
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                // Solo responden si SWAGGER_ENABLED=true
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                // Apartamentos
                .requestMatchers(HttpMethod.GET, "/api/apartamentos/*").access(autenticado())
                // La lista también la usa el contador (cartera)
                .requestMatchers(HttpMethod.GET, "/api/apartamentos").access(cartera())
                // Citófonos: quien vive en el apartamento (ApartamentoServiceImpl lo comprueba)
                .requestMatchers(HttpMethod.PUT, "/api/apartamentos/*/citofonos").access(autenticado())
                .requestMatchers("/api/apartamentos", "/api/apartamentos/**").access(admin())

                // Residentes: el propietario ve los de su apartamento y agrega o quita arrendatarios
                // (ResidenteServiceImpl lo comprueba); aprobar, dar acceso y editar es del administrador
                .requestMatchers(HttpMethod.GET, "/api/residentes/apartamento/*", "/api/residentes/apartamento/*/arrendatario").access(autenticado())
                // P5: personas del hogar, acceso y aprobación del arrendatario por su propietario
                .requestMatchers(HttpMethod.PUT, "/api/residentes/*", "/api/residentes/*/acceso",
                        "/api/residentes/*/aprobacion-propietario").access(autenticado())
                .requestMatchers(HttpMethod.POST, "/api/residentes").access(autenticado())
                .requestMatchers(HttpMethod.DELETE, "/api/residentes/*").access(autenticado())
                .requestMatchers("/api/residentes", "/api/residentes/**").access(admin())

                // Vehículos (el propietario solo los suyos)
                // Aprobación y cupos: solo el administrador (los cupos los puede ver cualquiera)
                .requestMatchers(HttpMethod.GET, "/api/vehiculos").access(admin())
                .requestMatchers(HttpMethod.PUT, "/api/vehiculos/*/estado", "/api/vehiculos/cupos",
                        "/api/vehiculos/cupos/apartamento/*").access(admin())
                // Parqueaderos (P5): solo el administrador
                .requestMatchers("/api/parqueaderos", "/api/parqueaderos/**").access(admin())
                .requestMatchers("/api/vehiculos/**").access(autenticado())

                // Obras
                .requestMatchers(HttpMethod.GET, "/api/obras", "/api/obras/en-proceso").access(admin())
                .requestMatchers(HttpMethod.PUT, "/api/obras/*/estado").access(admin())
                .requestMatchers(HttpMethod.PUT, "/api/obras/*/aprobacion").access(admin())
                // Borrar: la administración o el propietario mientras no esté aprobada (ObraServiceImpl)
                .requestMatchers("/api/obras", "/api/obras/**").access(autenticado())

                // Personal de obra
                .requestMatchers(HttpMethod.GET, "/api/personal-obra").access(admin())
                .requestMatchers(HttpMethod.PUT, "/api/personal-obra/*/aprobacion").access(admin())
                // Borrar: el administrador o el residente dueño de la obra (PersonalObraServiceImpl)
                .requestMatchers("/api/personal-obra", "/api/personal-obra/**").access(autenticado())

                // Zonas comunes
                .requestMatchers(HttpMethod.GET, "/api/zonas", "/api/zonas/**").access(autenticado())
                .requestMatchers("/api/zonas", "/api/zonas/**").access(admin())

                // Reservas
                .requestMatchers(HttpMethod.GET, "/api/reservas").access(admin())
                .requestMatchers(HttpMethod.PUT, "/api/reservas/*/estado").access(admin())
                // Borrar: el administrador cualquiera; el residente cancela la suya si no ha empezado
                .requestMatchers("/api/reservas", "/api/reservas/**").access(autenticado())

                // Pagos
                // P6: pago en línea. El webhook de Wompi es público y se valida por su firma
                .requestMatchers(HttpMethod.POST, "/api/pagos/webhook/wompi").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pagos/checkout").access(residentes())
                .requestMatchers(HttpMethod.GET, "/api/pagos/apartamento/*").access(autenticado())
                .requestMatchers(HttpMethod.PUT, "/api/pagos/apartamento/*/simular").access(autenticado())
                .requestMatchers("/api/pagos", "/api/pagos/**").access(cartera())

                // Cuotas mensuales (P4 bloque 7): el residente ve las suyas y sube el comprobante
                // (CuotaServiceImpl comprueba el apartamento); generar y revisar es del contador
                .requestMatchers(HttpMethod.GET, "/api/cuotas/apartamento/*", "/api/cuotas/*/comprobante").access(autenticado())
                .requestMatchers(HttpMethod.POST, "/api/cuotas/*/comprobante").access(autenticado())
                // P6: el contador lee y propone (generar, revisar → solicitudes); editar o borrar una cuota es del administrador
                .requestMatchers(HttpMethod.PUT, "/api/cuotas/*").access(admin())
                .requestMatchers(HttpMethod.DELETE, "/api/cuotas/*").access(admin())
                .requestMatchers("/api/cuotas", "/api/cuotas/**").access(cartera())
                // Enlaces firmados del almacenamiento local (demo): la firma hace de permiso
                .requestMatchers(HttpMethod.GET, "/api/archivos/local").permitAll()

                // Cuentas de la administración (administradores y contador)
                // Solo el principal (AdministradorController lo comprueba); los asistentes son ADMIN
                .requestMatchers("/api/administradores", "/api/administradores/**").access(admin())
                // Cartera de la administración (P5): estadísticas y solicitudes del contador; revisar, el administrador
                .requestMatchers(HttpMethod.PUT, "/api/cartera/solicitudes/*").access(admin())
                .requestMatchers("/api/cartera", "/api/cartera/**").access(cartera())

                // Chat (P5): ChatService decide quién ve qué conversación; el contador no entra
                .requestMatchers("/api/chat", "/api/chat/**").access(autenticado())

                // Notificaciones
                // Cada uno ve las suyas: el administrador, las suyas; un residente, las de su apartamento
                .requestMatchers("/api/notificaciones", "/api/notificaciones/**").access(sesion())
                // Portería (P6): la portería registra y consulta; el residente responde las de su apartamento
                .requestMatchers(HttpMethod.GET, "/api/porteria/visitas/pendientes").access(residentes())
                .requestMatchers(HttpMethod.PATCH, "/api/porteria/visitas/*/respuesta").access(respondeVisita())
                .requestMatchers("/api/porteria", "/api/porteria/**").access(portero())
                // Celulares que reciben avisos: cualquiera con sesión (DispositivoServiceImpl la exige).
                // La baja no pide sesión: se hace al cerrarla y basta con el token, que solo tiene ese celular
                .requestMatchers(HttpMethod.POST, "/api/dispositivos/baja").permitAll()
                .requestMatchers("/api/dispositivos", "/api/dispositivos/**").access(sesion())
                // Feed (P5): lo ven todos; publica, edita y borra la administración
                // P6: la portería no ve el feed; abrir una publicación la marca como leída
                .requestMatchers(HttpMethod.GET, "/api/publicaciones").access(sinPorteria())
                .requestMatchers(HttpMethod.POST, "/api/publicaciones/*/lectura").access(sinPorteria())
                .requestMatchers("/api/publicaciones", "/api/publicaciones/**").access(admin())
                // Visitantes frecuentes: cada apartamento los suyos (VisitanteController lo comprueba)
                .requestMatchers("/api/visitantes", "/api/visitantes/**").access(autenticado())

                .anyRequest().access(autenticado()));

        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> admin() {
        return regla(AuthorityAuthorizationManager.hasRole(TokenService.ROL_ADMIN));
    }

    private AuthorizationManager<RequestAuthorizationContext> cartera() {
        return regla(AuthorityAuthorizationManager.hasAnyRole(TokenService.ROL_ADMIN, TokenService.ROL_CONTADOR));
    }

    private AuthorizationManager<RequestAuthorizationContext> sinPorteria() {
        return regla(AuthorityAuthorizationManager.hasAnyRole(TokenService.ROL_ADMIN, TokenService.ROL_CONTADOR,
                TokenService.ROL_PROPIETARIO, TokenService.ROL_ARRENDATARIO));
    }

    /** Cualquier sesión menos la portería: la portería solo ve lo suyo (P6). */
    private AuthorizationManager<RequestAuthorizationContext> autenticado() {
        return regla((auth, ctx) -> {
            AuthorizationDecision autenticado = AuthenticatedAuthorizationManager.authenticated().check(auth, ctx);
            boolean porteria = auth.get().getAuthorities().stream()
                    .anyMatch(a -> ("ROLE_" + TokenService.ROL_PORTERIA).equals(a.getAuthority()));
            return new AuthorizationDecision(autenticado != null && autenticado.isGranted() && !porteria);
        });
    }

    /** Cualquier sesión, también la portería: cuenta, avisos, legal. */
    private AuthorizationManager<RequestAuthorizationContext> sesion() {
        return regla(AuthenticatedAuthorizationManager.authenticated());
    }

    /** Administración y portería. */
    private AuthorizationManager<RequestAuthorizationContext> portero() {
        return regla(AuthorityAuthorizationManager.hasAnyRole(TokenService.ROL_ADMIN, TokenService.ROL_PORTERIA));
    }

    /** Quien puede responder una visita: la administración, la portería (por llamada) y los residentes. */
    private AuthorizationManager<RequestAuthorizationContext> respondeVisita() {
        return regla(AuthorityAuthorizationManager.hasAnyRole(TokenService.ROL_ADMIN, TokenService.ROL_PORTERIA,
                TokenService.ROL_PROPIETARIO, TokenService.ROL_ARRENDATARIO));
    }

    private AuthorizationManager<RequestAuthorizationContext> residentes() {
        return regla(AuthorityAuthorizationManager.hasAnyRole(TokenService.ROL_PROPIETARIO, TokenService.ROL_ARRENDATARIO));
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
        return req -> req.getRequestURI().startsWith("/api/auth/")
                && (req.getRequestURI().endsWith("/login") || req.getRequestURI().startsWith("/api/auth/registro"))
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
