package com.finflow.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthenticatedReactiveAuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Reactive security for the API Gateway: public routes are open; everything else requires a valid JWT.
 * <p>
 * OAuth2 Resource Server validates bearer tokens. The gateway does not create HTTP sessions
 * ({@link NoOpServerSecurityContextRepository}) — stateless, suitable for REST. CSRF is disabled
 * because there is no server-side session to bind tokens to.
 * </p>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Builds the {@link SecurityWebFilterChain} using {@link ServerHttpSecurity}.
     * Authorization uses {@link org.springframework.security.authorization.ReactiveAuthorizationManager}
     * (e.g. {@link AuthenticatedReactiveAuthorizationManager} for protected routes).
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/actuator/**",
                                "/actuator/health",
                                "/actuator/prometheus",
                                "/auth/**",
                                "/fallback/**"
                        ).permitAll()
                        .anyExchange().access(AuthenticatedReactiveAuthorizationManager.authenticated())
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
