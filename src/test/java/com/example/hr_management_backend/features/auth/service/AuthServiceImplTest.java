package com.example.hr_management_backend.features.auth.service;

import com.example.hr_management_backend.core.security.JwtUtils;
import com.example.hr_management_backend.features.auth.dto.LoginRequest;
import com.example.hr_management_backend.features.auth.dto.LoginResponse;
import com.example.hr_management_backend.features.auth.dto.RegisterRequest;
import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded_pass")
                .role("EMPLOYEE")
                .employeeId(10L)
                .build();
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("test@example.com", "password");
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtUtils.generateJwtToken(authentication)).thenReturn("mocked-jwt-token");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked-jwt-token", response.getToken());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("EMPLOYEE", response.getRole());
        assertEquals(10L, response.getEmployeeId());
    }

    @Test
    void register_Success() {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("new@example.com")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .department("IT")
                .role("DEVELOPER")
                .build();

        Employee createdEmployee = new Employee();
        createdEmployee.setId(20L);

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenReturn(createdEmployee);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pass");

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtUtils.generateJwtToken(authentication)).thenReturn("mocked-jwt-token");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.of(sampleUser));

        LoginResponse response = authService.register(registerRequest);

        assertNotNull(response);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("test@example.com")
                .password("password")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }
}
