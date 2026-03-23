package com.project.fnb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.cors.CorsConfigurationSource;

import com.project.fnb.infrastructure.security.AccessKeyAuthenticationFilter;
import com.project.fnb.infrastructure.security.AccessKeyAuthenticationProvider;
import com.project.fnb.infrastructure.security.KeycloakJwtAuthenticationConverter;

import lombok.RequiredArgsConstructor;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
        private String jwkSetUri;

        private final CorsConfigurationSource corsConfigurationSource;
        private final AccessKeyAuthenticationProvider accessKeyAuthenticationProvider;

        @Bean
        public JwtDecoder jwtDecoder() {
                return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }

        @Bean
        public ProviderManager providerManager() {
                JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(jwtDecoder());
                jwtProvider.setJwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter());
                return new ProviderManager(Arrays.asList(jwtProvider, accessKeyAuthenticationProvider));
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, ProviderManager providerManager) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .csrf(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/api/public/**",
                                                                "/actuator/**",
                                                                "/ws/**")  // WebSocket endpoint
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/tenants/**").permitAll()
                                                .requestMatchers("/api/pos/orders/**").permitAll()
                                                .requestMatchers("/api/pos/public/**").permitAll()
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwkSetUri(jwkSetUri)
                                                                .jwtAuthenticationConverter(
                                                                                new KeycloakJwtAuthenticationConverter())))
                                .addFilterBefore(new AccessKeyAuthenticationFilter(providerManager),
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}