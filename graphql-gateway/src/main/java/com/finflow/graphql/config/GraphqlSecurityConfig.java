package com.finflow.graphql.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security config for GraphQL Gateway.
 *
 * <p>Key design decision: /graphql endpoint is permitted at the security filter level. This
 * allows: 1. GraphQL introspection queries (used by GraphiQL UI) 2. Individual resolvers to
 * handle their own authorization
 *
 * <p>In production, introspection should be disabled:
 * spring.graphql.schema.introspection.enabled=false
 *
 * <p>JWT is extracted from the Authorization: Bearer header by Spring Security's OAuth2 resource
 * server support. The authenticated principal is available in resolvers via @AuthenticationPrincipal
 * Jwt jwt parameter.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class GraphqlSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/**",
                                                "/actuator/health",
                                                "/actuator/prometheus",
                                                "/graphiql/**",
                                                "/graphql")
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Collections.singletonList("*"));
        config.setAllowedMethods(Collections.singletonList("*"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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
