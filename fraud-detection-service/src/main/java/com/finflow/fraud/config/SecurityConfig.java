package com.finflow.fraud.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource server for the Fraud Detection Service HTTP surface. JWT validation with
 * Keycloak {@code realm_access.roles} mapped to {@code ROLE_*} authorities; same pattern as
 * transaction-service.
 *
 * <p><strong>gRPC is not protected by this filter chain.</strong> gRPC endpoints are secured at the
 * network level (internal VPC / mesh only), not through Spring Security.
 */
@Configuration
@EnableWebSecurity
@Profile("!itest")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/**",
                                                "/actuator/health",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt -> {
                                            Customizer.withDefaults().customize(jwt);
                                            jwt.jwtAuthenticationConverter(jwtAuthenticationConverter);
                                        }));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtRealmRoleAuthoritiesConverter());
        return converter;
    }

    private Converter<Jwt, Collection<GrantedAuthority>> jwtRealmRoleAuthoritiesConverter() {
        return jwt -> {
            Object claim = jwt.getClaim("realm_access");
            if (!(claim instanceof Map<?, ?> realmAccess)) {
                return Collections.emptyList();
            }
            Object rolesObj = realmAccess.get("roles");
            if (!(rolesObj instanceof Collection<?> roles)) {
                return Collections.emptyList();
            }
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            for (Object role : roles) {
                if (role == null) {
                    continue;
                }
                String name = role.toString();
                if (!name.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + name));
                }
            }
            return authorities;
        };
    }
}
