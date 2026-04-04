package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.storage.StorageService;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private UserService userService;

    @Test
    void updateAvatar_ShouldThrow_WhenUserNotFound() {
        when(userRepository.findById("u-1")).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2});

        AppException ex = assertThrows(AppException.class, () -> userService.updateAvatar("u-1", file));

        assertEquals(404, ex.getErrorCode());
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateAvatar_ShouldReplaceOldAvatar_WhenOldAvatarExists() {
        User user = User.builder().id("u-1").avatarUrl("http://cdn/old.png").build();
        when(userRepository.findById("u-1")).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        when(storageService.uploadUserProfileImage(file)).thenReturn("http://cdn/new.png");

        String result = userService.updateAvatar("u-1", file);

        assertEquals("http://cdn/new.png", result);
        assertEquals("http://cdn/new.png", user.getAvatarUrl());
        verify(storageService).deleteFile("http://cdn/old.png");
        verify(userRepository).save(user);
    }

    @Test
    void updateAvatar_ShouldSkipDelete_WhenOldAvatarMissing() {
        User user = User.builder().id("u-1").avatarUrl(null).build();
        when(userRepository.findById("u-1")).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});
        when(storageService.uploadUserProfileImage(file)).thenReturn("http://cdn/new.png");

        userService.updateAvatar("u-1", file);

        verify(storageService, never()).deleteFile(anyString());
        verify(userRepository).save(user);
    }
}
