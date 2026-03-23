package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.storage.StorageService;
import com.project.fnb.modules.global.dto.UserResponse;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user-123")
                .email("test@example.com")
                .fullName("Test User")
                .trustScore(100)
                .build();

        jwt = mock(Jwt.class);
    }

    @Test
    void syncUserFromToken_WhenUserExists_ShouldUpdateAndReturnUser() {
        // Arrange
        when(jwt.getSubject()).thenReturn("user-123");
        when(jwt.getClaimAsString("email")).thenReturn("newemail@example.com");
        when(jwt.getClaimAsString("name")).thenReturn("Updated Name");
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        UserResponse result = userService.syncUserFromToken(jwt);

        // Assert
        assertNotNull(result);
        assertEquals("user-123", result.getId());
        assertEquals("newemail@example.com", testUser.getEmail());
        assertEquals("Updated Name", testUser.getFullName());
        verify(userRepository).save(testUser);
    }

    @Test
    void syncUserFromToken_WhenUserDoesNotExist_ShouldCreateAndReturnNewUser() {
        // Arrange
        when(jwt.getSubject()).thenReturn("new-user");
        when(jwt.getClaimAsString("email")).thenReturn("new@example.com");
        when(jwt.getClaimAsString("name")).thenReturn("New User");
        when(userRepository.findById("new-user")).thenReturn(Optional.empty());

        User newUser = User.builder()
                .id("new-user")
                .email("new@example.com")
                .fullName("New User")
                .trustScore(100)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // Act
        UserResponse result = userService.syncUserFromToken(jwt);

        // Assert
        assertNotNull(result);
        assertEquals("new-user", result.getId());
        assertEquals("new@example.com", result.getEmail());
        assertEquals("New User", result.getFullName());
        assertEquals(100, result.getTrustScore());
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void syncUserFromToken_WhenNameIsNull_ShouldUsePreferredUsername() {
        // Arrange
        when(jwt.getSubject()).thenReturn("new-user");
        when(jwt.getClaimAsString("email")).thenReturn("new@example.com");
        when(jwt.getClaimAsString("name")).thenReturn(null);
        when(jwt.getClaimAsString("preferred_username")).thenReturn("preferred_user");
        when(userRepository.findById("new-user")).thenReturn(Optional.empty());

        User newUser = User.builder()
                .id("new-user")
                .email("new@example.com")
                .fullName("preferred_user")
                .trustScore(100)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // Act
        UserResponse result = userService.syncUserFromToken(jwt);

        // Assert
        assertNotNull(result);
        assertEquals("preferred_user", result.getFullName());
    }

    @Test
    void updateAvatar_WhenUserExists_ShouldUpdateAvatarUrl() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test image content".getBytes());
        String newAvatarUrl = "http://example.com/new-avatar.jpg";

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(storageService.uploadUserProfileImage(file)).thenReturn(newAvatarUrl);

        // Act
        String result = userService.updateAvatar("user-123", file);

        // Assert
        assertEquals(newAvatarUrl, result);
        assertEquals(newAvatarUrl, testUser.getAvatarUrl());
        verify(storageService, never()).deleteFile(any());
        verify(userRepository).save(testUser);
    }
    
    @Test
    void updateAvatar_WhenUserExistsWithOldAvatar_ShouldDeleteOldAndUploadNew() {
        // Arrange
        testUser.setAvatarUrl("http://example.com/old-avatar.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test image content".getBytes());
        String newAvatarUrl = "http://example.com/new-avatar.jpg";

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(storageService.uploadUserProfileImage(file)).thenReturn(newAvatarUrl);

        // Act
        String result = userService.updateAvatar("user-123", file);

        // Assert
        assertEquals(newAvatarUrl, result);
        assertEquals(newAvatarUrl, testUser.getAvatarUrl());
        verify(storageService).deleteFile("http://example.com/old-avatar.jpg");
        verify(userRepository).save(testUser);
    }

    @Test
    void updateAvatar_WhenUserNotFound_ShouldThrowAppException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test image content".getBytes());
        when(userRepository.findById("non-existent")).thenReturn(Optional.empty());

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            userService.updateAvatar("non-existent", file);
        });
        assertEquals(404, exception.getErrorCode());
        assertEquals("User not found", exception.getMessage());
    }
}