package com.project.fnb.infrastructure.security;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    @Test
    void loadUserByUsername_ShouldLoadByUsername() {
        User user = User.builder()
                .id("u-1")
                .username("owner")
                .email("owner@shop.com")
                .password("hashed")
                .roles("USER,ADMIN")
                .build();

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(user));

        UserDetails details = appUserDetailsService.loadUserByUsername("owner");

        assertNotNull(details);
        assertEquals("owner", details.getUsername());
        Set<String> authorities = details.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
        assertTrue(authorities.contains("ROLE_USER"));
        assertTrue(authorities.contains("ROLE_ADMIN"));
    }

    @Test
    void loadUserByUsername_ShouldFallbackToEmail() {
        User user = User.builder()
                .id("u-2")
                .username("waiter")
                .email("waiter@shop.com")
                .password("hashed")
                .roles("USER")
                .build();

        when(userRepository.findByUsername("waiter@shop.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("waiter@shop.com")).thenReturn(Optional.of(user));

        UserDetails details = appUserDetailsService.loadUserByUsername("waiter@shop.com");

        assertEquals("waiter", details.getUsername());
    }

    @Test
    void loadUserByUsername_ShouldThrow_WhenPasswordMissing() {
        User user = User.builder()
                .id("u-3")
                .username("nopass")
                .email("nopass@shop.com")
                .password(" ")
                .build();

        when(userRepository.findByUsername("nopass")).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class,
                () -> appUserDetailsService.loadUserByUsername("nopass"));

        assertEquals(401, ex.getErrorCode());
    }

    @Test
    void loadUserByUsername_ShouldThrow_WhenNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> appUserDetailsService.loadUserByUsername("ghost"));
    }
}
