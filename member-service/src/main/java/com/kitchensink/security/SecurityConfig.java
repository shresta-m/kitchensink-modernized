package com.kitchensink.security;

import com.kitchensink.common.config.AppProperties;
import com.kitchensink.common.exception.ErrorCode;
import com.kitchensink.common.logging.ApiLoggingAspect;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless JWT security. Access rules, in one place:
 * <ul>
 *   <li>public: register, login, API docs, health probes;</li>
 *   <li>any signed-in member: /auth/me, /auth/change-password, /auth/logout;</li>
 *   <li>ADMIN or VIEWER: read and search members and the lookups (GET);</li>
 *   <li>ADMIN only: export (the whole member list with phone numbers in one file) and every other members operation
 *       (create, update, role, reset password, delete, bulk delete);</li>
 *   <li>everything else is denied.</li>
 * </ul>
 * CSRF protection is off on purpose: there are no cookies or sessions, only a bearer token in a header.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** A URL on purpose: some Spring Security versions convert the issuer claim to a URL. */
    static final String ISSUER = "https://kitchensink.local";
    private static final int MIN_SECRET_BYTES = 32;     // HS256 needs a 256-bit key
    private static final String ADMIN = "ADMIN";
    private static final String VIEWER = "VIEWER";

    /** An API never needs the browser's sensors or payment sheet; turning them off costs nothing. */
    static final String PERMISSIONS_POLICY = "geolocation=(), camera=(), microphone=(), payment=()";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, MemberJwtAuthenticationConverter converter,
                                    @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        // 401 and 403 raised by the filters are handed to GlobalExceptionHandler, so they use the normal error shape
        AuthenticationEntryPoint unauthorized = (req, res, ex) -> {
            ApiLoggingAspect.logRejected(req, ErrorCode.UNAUTHENTICATED);
            resolver.resolveException(req, res, null, ex);
        };
        AccessDeniedHandler forbidden = (req, res, ex) -> {
            ApiLoggingAspect.logRejected(req, ErrorCode.ACCESS_DENIED);
            resolver.resolveException(req, res, null, ex);
        };
        return http
                .csrf(AbstractHttpConfigurer::disable) // stateless bearer-JWT API, no cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password", "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/members/export").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/members", "/api/v1/members/**", "/api/v1/lookups/**")
                                .hasAnyRole(ADMIN, VIEWER)
                        .requestMatchers("/api/v1/members", "/api/v1/members/**").hasRole(ADMIN)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "traceparent", "tracestate"));
        cors.setExposedHeaders(List.of("Content-Disposition", "X-Trace-Id", "Retry-After"));   // CSV file name, trace id to quote in a bug report, and when a lockout ends
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    JwtEncoder jwtEncoder(AppProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties.jwt().secret())));
    }

    @Bean
    JwtDecoder jwtDecoder(AppProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties.jwt().secret()))
                .macAlgorithm(MacAlgorithm.HS256).build();
        // zero clock skew: a 30 minute token is really valid for 30 minutes
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(ISSUER)));
        return decoder;
    }

    /** Fails at startup, not at the first login, when the secret is missing or too weak. */
    static SecretKey signingKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret (environment variable JWT_SECRET) must be set to at least 32 characters");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth")
                );
    }
}
