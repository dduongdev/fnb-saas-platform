package com.project.fnb.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri; 

    @Value("${keycloak.admin.client-id}") 
    private String adminClientId;

    @Value("${keycloak.admin.client-secret}")
    private String adminClientSecret; 

    @Bean
    public Keycloak keycloakAdmin() {
        String serverUrl = issuerUri.substring(0, issuerUri.indexOf("/realms"));
        String realm = issuerUri.substring(issuerUri.lastIndexOf("/") + 1);

        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(adminClientId)
                .clientSecret(adminClientSecret)
                .build();
    }
}