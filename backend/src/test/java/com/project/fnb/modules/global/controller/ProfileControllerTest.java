package com.project.fnb.modules.global.controller;

import com.project.fnb.modules.global.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private ProfileController profileController;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        Jwt mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(USER_ID)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(profileController)
                .setCustomArgumentResolvers(new TestJwtArgumentResolver(mockJwt))
                .build();
    }

    @Test
    void uploadAvatar_ShouldReturnNewUrl() throws Exception {
        String expectedUrl = "http://minio.local/tenant-assets/avatar-new.jpg";

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "fake-image-data".getBytes());

        when(userService.updateAvatar(eq(USER_ID), any())).thenReturn(expectedUrl);

        mockMvc.perform(multipart("/api/profile/avatar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(expectedUrl));

        verify(userService).updateAvatar(eq(USER_ID), any());
    }
}
