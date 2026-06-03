package com.project.fnb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.project.fnb.infrastructure.security.AccessKeyAuthenticationFilter;
import com.project.fnb.infrastructure.security.AccessKeyAuthenticationProvider;
import com.project.fnb.infrastructure.security.AppUserDetailsService;

import lombok.RequiredArgsConstructor;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.jwt.secret:ZmFrZV9zZWNyZXRfZm9yX2RldmVsb3BtZW50X2VuY29kaW5nMTIzNA==}")
    private String jwtSecret;

    private final CorsConfigurationSource corsConfigurationSource;
    private final AccessKeyAuthenticationProvider accessKeyAuthenticationProvider;
    private final AppUserDetailsService appUserDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(appUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter jwtAuthenticationConverter() {
        org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter jwtAuthenticationConverter = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Static resources (React SPA)
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/logo.svg",
                                "/manifest.json")
                        .permitAll()
                        // SPA client-side routes — cho phép React Router xử lý
                        // Match các path không phải API/actuator/ws/assets
                        .requestMatchers(HttpMethod.GET,
                                "/",
                                "/login",
                                "/register",
                                "/shops",
                                "/access-key-login",
                                "/dashboard",
                                "/dashboard/**",
                                "/pos",
                                "/pos/**",
                                "/tables",
                                "/tables/**",
                                "/table/**",
                                "/sessions",
                                "/sessions/**",
                                "/menu/**",
                                "/products",
                                "/products/**",
                                "/categories",
                                "/categories/**",
                                "/reports",
                                "/reports/**",
                                "/settings",
                                "/settings/**",
                                "/notifications",
                                "/notifications/**",
                                "/customer/**",
                                "/kds",
                                "/kds/**")
                        .permitAll()
                        // Public API endpoints
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/api/public/**",
                                "/actuator/**",
                                "/ws/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers("/api/pos/public/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder()).jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .authenticationProvider(accessKeyAuthenticationProvider)
                .authenticationProvider(daoAuthenticationProvider())
                .addFilterBefore(new AccessKeyAuthenticationFilter(new org.springframework.security.authentication.ProviderManager(Arrays.asList(accessKeyAuthenticationProvider))),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
