package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.global.dto.RegisterUserRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class KeycloakAdminService {

    @Value("${KEYCLOAK_URL:http://keycloak:8080}")
    private String keycloakUrl;

    @Value("${KEYCLOAK_ISSUER_URI:http://keycloak:8080/realms/fnb-platform}")
    private String issuerUri;

    @Value("${KEYCLOAK_ADMIN_CLIENT_ID:fnb-backend}")
    private String adminClientId;

    @Value("${KEYCLOAK_ADMIN_CLIENT_SECRET}")
    private String adminClientSecret;

    @Value("${KEYCLOAK_REALM:fnb-platform}")
    private String realm;

    private final RestTemplate restTemplate = new RestTemplate();

    private String fetchAdminToken() {
        String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=client_credentials&client_id=" + adminClientId + "&client_secret=" + adminClientSecret;
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new AppException(500, "Không lấy được token admin Keycloak");
        }

        Object token = response.getBody().get("access_token");
        if (token == null) {
            throw new AppException(500, "Không lấy được access token Keycloak");
        }
        return token.toString();
    }

    public void registerUser(RegisterUserRequest request) {
        String accessToken = fetchAdminToken();

        String userUrl = keycloakUrl + "/admin/realms/" + realm + "/users";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, Object> payload = new HashMap<>();
        payload.put("username", request.getUsername());
        payload.put("email", request.getEmail());
        payload.put("enabled", true);
        payload.put("firstName", request.getFullName());
        payload.put("emailVerified", false);

        Map<String, Object> cred = new HashMap<>();
        cred.put("type", "password");
        cred.put("value", request.getPassword());
        cred.put("temporary", false);

        payload.put("credentials", new Object[]{cred});

        HttpEntity<Map<String, Object>> createReq = new HttpEntity<>(payload, headers);
        ResponseEntity<Void> createRes = restTemplate.postForEntity(userUrl, createReq, Void.class);
        if (createRes.getStatusCode() != HttpStatus.CREATED) {
            log.warn("Keycloak register failed status={} body={}", createRes.getStatusCodeValue(), createRes.getBody());
            throw new AppException(500, "Tạo người dùng thất bại");
        }
    }
}
