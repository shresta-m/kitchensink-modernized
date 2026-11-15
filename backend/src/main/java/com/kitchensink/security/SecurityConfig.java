package com.kitchensink.security;



import com.kitchensink.config.AppProperties;
import com.kitchensink.observability.AccessLogFilter;
import java.time.Clock;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Stateless JWT security: the URL rules and the filter chain. Token and CORS beans live in
 * {@link JwtConfig} and {@link CorsConfig}. CSRF protection is off on purpose: authentication is a bearer token sent in a
 * header (never a cookie), so a forged cross-site request cannot carry credentials.
 */
@Configuration
public class SecurityConfig {

    private final AppProperties.Security props;

    public SecurityConfig(AppProperties appProperties) {
        this.props = appProperties.security();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ProblemResponseWriter problems, ObjectMapper mapper,
            Clock clock) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable) // NOSONAR stateless bearer-token API: no cookies, so nothing for CSRF to forge
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> {
                a.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                a.requestMatchers("/error").permitAll();
                a.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register",
                        "/api/v1/auth/register-admin").permitAll();
                a.requestMatchers(EndpointRequest.to("health", "info")).permitAll();
                a.requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole("ADMIN");
                if (props.docsPublic()) {
                    a.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                } else {
                    a.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("ADMIN");
                }
                a.requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated();
                a.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                // Order matters: the template download is a write-role action even though it is a GET.
                a.requestMatchers(HttpMethod.GET, "/api/v1/members/import/template").hasRole("ADMIN");
                a.requestMatchers(HttpMethod.GET, "/api/v1/members/**").hasAnyRole("ADMIN", "VIEWER");
                a.requestMatchers("/api/v1/members/**").hasRole("ADMIN");
                a.anyRequest().denyAll();
            })
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
            .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
            .addFilterAfter(new AuthRateLimitFilter(
                    new FixedWindowRateLimiter(props.authRequestsPerMinute(), 60, clock), mapper), CorsFilter.class)
            .addFilterAfter(new AccessLogFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
