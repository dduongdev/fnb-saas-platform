package com.project.fnb.modules.global.controller;

import com.project.fnb.modules.global.dto.UserResponse;
import com.project.fnb.modules.global.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setCustomArgumentResolvers(new TestJwtArgumentResolver(createMockJwt()))
                .build();
    }

    private Jwt createMockJwt() {
        mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .claim("preferred_username", "testuser")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return mockJwt;
    }

    @Test
    void syncUser_ShouldReturnUserResponse() throws Exception {
        UserResponse response = UserResponse.builder()
                .id("user-123")
                .email("test@example.com")
                .fullName("Test User")
                .avatarUrl(null)
                .trustScore(100)
                .build();

        when(userService.syncUserFromToken(any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("user-123"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Test User"))
                .andExpect(jsonPath("$.data.trustScore").value(100));

        verify(userService).syncUserFromToken(any(Jwt.class));
    }
}
