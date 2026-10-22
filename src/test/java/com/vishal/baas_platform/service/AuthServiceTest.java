package com.vishal.baas_platform.service;

import com.vishal.baas_platform.dto.auth.LoginRequest;
import com.vishal.baas_platform.dto.auth.SignupRequest;
import com.vishal.baas_platform.entity.User;
import com.vishal.baas_platform.exception.CustomException;
import com.vishal.baas_platform.repository.UserRepository;
import com.vishal.baas_platform.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserRepository userRepository;
    private JwtUtil jwtUtil;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtUtil = mock(JwtUtil.class);
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
    }

    private static SignupRequest signup(String email, String password) {
        SignupRequest request = new SignupRequest();
        request.setName("Dev");
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private static LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void signupStoresAHashedPassword() {
        when(userRepository.existsByEmail("dev@example.com")).thenReturn(false);

        authService.signup(signup("dev@example.com", "secret123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void signupRejectsADuplicateEmail() {
        when(userRepository.existsByEmail("dev@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(signup("dev@example.com", "secret123")))
                .isInstanceOf(CustomException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithTheRightPasswordReturnsAToken() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com")
                .password(passwordEncoder.encode("secret123")).build();
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user.getId(), "dev@example.com")).thenReturn("jwt");

        assertThat(authService.login(login("dev@example.com", "secret123")).getToken()).isEqualTo("jwt");
    }

    @Test
    void loginWithAWrongPasswordFails() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com")
                .password(passwordEncoder.encode("secret123")).build();
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(login("dev@example.com", "wrong")))
                .isInstanceOf(CustomException.class);
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void loginWithAnUnknownEmailFails() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(login("nobody@example.com", "secret123")))
                .isInstanceOf(CustomException.class);
    }
}
