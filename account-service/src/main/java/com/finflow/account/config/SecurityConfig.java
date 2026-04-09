package com.finflow.account.config;

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
 * Secures the Account Service as a servlet (Spring MVC) OAuth2 resource server. JWTs are validated
 * locally; role names are taken from the Keycloak {@code realm_access.roles} claim and exposed to
 * Spring Security as {@code ROLE_*} authorities.
 */
@Configuration
@EnableWebSecurity
@Profile("!itest")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
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

                //Without this, Spring would authenticate the token but not know which roles/permissions the user has.
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt -> {
                                            Customizer.withDefaults().customize(jwt);
                                            jwt.jwtAuthenticationConverter(jwtAuthenticationConverter);
                                        }));
        return http.build();
    }

    /**
     * Maps Keycloak {@code realm_access.roles} to Spring {@link GrantedAuthority} with a
     * {@code ROLE_} prefix.
     */
    //Spring Security needs a way to understand the user’s roles/authorities from a JWT. This bean does exactly that.
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


    /*
    How It Fits Together
JWT is validated by Spring Security.
jwtAuthenticationConverter converts JWT → Authentication object.
jwtRealmRoleAuthoritiesConverter extracts roles from the JWT.
Each role is converted to ROLE_*.
Spring Security uses these roles for authorization.

Why it’s written: Without this converter, Spring knows the token is valid but doesn’t know the user’s roles, so your @PreAuthorize or hasRole() checks won’t work.
     */

