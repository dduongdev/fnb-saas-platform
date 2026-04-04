package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.JwtTokenProvider;
import com.project.fnb.modules.global.dto.LoginRequest;
import com.project.fnb.modules.global.dto.LoginResponse;
import com.project.fnb.modules.global.dto.RegisterUserRequest;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerUser_ShouldSaveUser_WhenRequestValid() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername("owner");
        request.setPassword("secret");
        request.setEmail("owner@shop.com");
        request.setFullName("Shop Owner");

        when(userRepository.findByUsername("owner")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("owner@shop.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");

        authService.registerUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("owner", saved.getUsername());
        assertEquals("owner@shop.com", saved.getEmail());
        assertEquals("encoded-secret", saved.getPassword());
        assertEquals("USER", saved.getRoles());
        assertEquals(100, saved.getTrustScore());
    }

    @Test
    void registerUser_ShouldThrow_WhenUsernameExists() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername("owner");
        request.setPassword("secret");

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(new User()));

        AppException ex = assertThrows(AppException.class, () -> authService.registerUser(request));

        assertEquals(400, ex.getErrorCode());
        assertEquals("username đã tồn tại", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_ShouldReturnToken_WhenCredentialsValid() {
        LoginRequest request = new LoginRequest();
        request.setUsername("owner");
        request.setPassword("secret");

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("jwt-token");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getAccessToken());
    }

    @Test
    void login_ShouldThrow_WhenBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setUsername("owner");
        request.setPassword("wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));

        assertEquals(401, ex.getErrorCode());
        assertEquals("Tên đăng nhập hoặc mật khẩu không đúng", ex.getMessage());
    }
}
