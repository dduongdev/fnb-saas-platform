package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.JwtTokenProvider;
import com.project.fnb.modules.global.dto.LoginRequest;
import com.project.fnb.modules.global.dto.LoginResponse;
import com.project.fnb.modules.global.dto.RegisterUserRequest;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void registerUser(RegisterUserRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank() ||
            request.getPassword() == null || request.getPassword().isBlank()) {
            throw new AppException(400, "username và password bắt buộc");
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new AppException(400, "username đã tồn tại");
        }

        if (request.getEmail() != null && userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(400, "email đã tồn tại");
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles("USER")
                .trustScore(100)
                .build();

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            throw new AppException(400, "username và password bắt buộc");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            String token = jwtTokenProvider.generateToken(authentication);
            return new LoginResponse(token);
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            throw new AppException(401, "Tên đăng nhập hoặc mật khẩu không đúng");
        }
    }
}
