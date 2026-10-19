package com.example.hr_management_backend.features.auth.service;

import com.example.hr_management_backend.core.security.CustomUserDetailsService;
import com.example.hr_management_backend.core.security.UserPrincipal;
import com.example.hr_management_backend.features.auth.dto.AccountStatusResponse;
import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.model.UserActivationToken;
import com.example.hr_management_backend.features.auth.repository.UserActivationTokenRepository;
import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeAuthorityRepository;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthLifecycleAndOptimizationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeAuthorityRepository employeeAuthorityRepository;

    @Mock
    private UserActivationTokenRepository userActivationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService(userRepository, employeeAuthorityRepository, employeeRepository);
    }

    @Test
    @DisplayName("getAccountStatus returns NOT_FOUND for unknown email")
    void testGetAccountStatus_NotFound() {
        when(userRepository.findByEmail("unknown@company.com")).thenReturn(Optional.empty());

        AccountStatusResponse response = authService.getAccountStatus("unknown@company.com");

        assertNotNull(response);
        assertEquals("NOT_FOUND", response.getStatus());
        assertFalse(response.getIsActivated());
    }

    @Test
    @DisplayName("getAccountStatus returns PENDING_ACTIVATION when user is not yet activated")
    void testGetAccountStatus_PendingActivation() {
        User pendingUser = User.builder()
                .id(1L)
                .email("pending@company.com")
                .isActivated(false)
                .accountStatus("PENDING_ACTIVATION")
                .employeeId(101L)
                .build();
        Employee emp = Employee.builder().id(101L).firstName("Aarav").build();

        when(userRepository.findByEmail("pending@company.com")).thenReturn(Optional.of(pendingUser));
        when(employeeRepository.findById(101L)).thenReturn(Optional.of(emp));

        AccountStatusResponse response = authService.getAccountStatus("pending@company.com");

        assertNotNull(response);
        assertEquals("PENDING_ACTIVATION", response.getStatus());
        assertFalse(response.getIsActivated());
        assertEquals("Aarav", response.getFirstName());
    }

    @Test
    @DisplayName("getAccountStatus returns ACTIVE for active employee")
    void testGetAccountStatus_Active() {
        User activeUser = User.builder()
                .id(2L)
                .email("active@company.com")
                .isActivated(true)
                .accountStatus("ACTIVE")
                .employeeId(102L)
                .build();
        Employee emp = Employee.builder().id(102L).firstName("Neha").build();

        when(userRepository.findByEmail("active@company.com")).thenReturn(Optional.of(activeUser));
        when(employeeRepository.findById(102L)).thenReturn(Optional.of(emp));

        AccountStatusResponse response = authService.getAccountStatus("active@company.com");

        assertNotNull(response);
        assertEquals("ACTIVE", response.getStatus());
        assertTrue(response.getIsActivated());
        assertEquals("Neha", response.getFirstName());
    }

    @Test
    @DisplayName("getAccountStatus returns DEACTIVATED for terminated employee")
    void testGetAccountStatus_Deactivated() {
        User deactivatedUser = User.builder()
                .id(3L)
                .email("left@company.com")
                .isActivated(true)
                .accountStatus("DEACTIVATED")
                .build();

        when(userRepository.findByEmail("left@company.com")).thenReturn(Optional.of(deactivatedUser));

        AccountStatusResponse response = authService.getAccountStatus("left@company.com");

        assertNotNull(response);
        assertEquals("DEACTIVATED", response.getStatus());
    }

    @Test
    @DisplayName("activateAccount succeeds on first-time activation and sets user to ACTIVE")
    void testActivateAccount_Success() {
        User user = User.builder()
                .id(4L)
                .email("newhire@company.com")
                .isActivated(false)
                .accountStatus("PENDING_ACTIVATION")
                .build();

        UserActivationToken token = UserActivationToken.builder()
                .id(10L)
                .userId(4L)
                .employeeId(200L)
                .used(false)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        when(userActivationTokenRepository.findFirstByActivationKeyHashOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(token));
        when(userActivationTokenRepository.markTokenUsedAtomically(anyLong(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("encoded_pass");

        String activatedEmail = authService.activateAccount("ACT-ABCD1234", "SecurePass123!");

        assertEquals("newhire@company.com", activatedEmail);
        assertTrue(user.getIsActivated());
        assertEquals("ACTIVE", user.getAccountStatus());
        assertTrue(token.getUsed());
        verify(userRepository).save(user);
        verify(userActivationTokenRepository).save(token);
        verify(userActivationTokenRepository).invalidateActiveTokensForUser(4L);
    }

    @Test
    @DisplayName("activateAccount rejects re-activation if user is already active")
    void testActivateAccount_AlreadyActive_ThrowsIllegalStateException() {
        User user = User.builder()
                .id(5L)
                .email("alreadyactive@company.com")
                .isActivated(true)
                .accountStatus("ACTIVE")
                .build();

        UserActivationToken token = UserActivationToken.builder()
                .id(11L)
                .userId(5L)
                .employeeId(201L)
                .used(false)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        when(userActivationTokenRepository.findFirstByActivationKeyHashOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                authService.activateAccount("ACT-OLD123", "NewPass123!"));

        assertTrue(ex.getMessage().contains("already been activated"));
        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("CustomUserDetailsService blocks terminated employee with DisabledException")
    void testUserDetailsService_TerminatedEmployee_ThrowsDisabledException() {
        User user = User.builder()
                .id(6L)
                .email("terminated@company.com")
                .password("pass")
                .role("EMPLOYEE")
                .employeeId(301L)
                .accountStatus("ACTIVE")
                .isActivated(true)
                .build();

        Employee emp = Employee.builder()
                .id(301L)
                .status("TERMINATED")
                .build();

        when(userRepository.findByEmail("terminated@company.com")).thenReturn(Optional.of(user));
        when(employeeRepository.findById(301L)).thenReturn(Optional.of(emp));

        assertThrows(DisabledException.class, () ->
                userDetailsService.loadUserByUsername("terminated@company.com"));

        assertEquals("DEACTIVATED", user.getAccountStatus());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("CustomUserDetailsService blocks expired notice period employee with DisabledException")
    void testUserDetailsService_ExpiredNoticePeriod_ThrowsDisabledException() {
        User user = User.builder()
                .id(7L)
                .email("relieved@company.com")
                .password("pass")
                .role("EMPLOYEE")
                .employeeId(302L)
                .accountStatus("ACTIVE")
                .isActivated(true)
                .build();

        Employee emp = Employee.builder()
                .id(302L)
                .status("NOTICE_PERIOD")
                .relievingDate(LocalDate.now().minusDays(1)) // Expired yesterday
                .build();

        when(userRepository.findByEmail("relieved@company.com")).thenReturn(Optional.of(user));
        when(employeeRepository.findById(302L)).thenReturn(Optional.of(emp));

        assertThrows(DisabledException.class, () ->
                userDetailsService.loadUserByUsername("relieved@company.com"));

        assertEquals("TERMINATED", emp.getStatus());
        assertEquals("DEACTIVATED", user.getAccountStatus());
        verify(employeeRepository).save(emp);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("CustomUserDetailsService blocks pending activation user with LockedException")
    void testUserDetailsService_PendingActivation_ThrowsLockedException() {
        User user = User.builder()
                .id(8L)
                .email("unactivated@company.com")
                .password("pass")
                .role("EMPLOYEE")
                .employeeId(303L)
                .accountStatus("PENDING_ACTIVATION")
                .isActivated(false)
                .build();

        Employee emp = Employee.builder()
                .id(303L)
                .status("ACTIVE")
                .build();

        when(userRepository.findByEmail("unactivated@company.com")).thenReturn(Optional.of(user));
        when(employeeRepository.findById(303L)).thenReturn(Optional.of(emp));
        when(employeeAuthorityRepository.findAuthoritiesByEmployeeId(303L)).thenReturn(Collections.emptyList());

        assertThrows(LockedException.class, () ->
                userDetailsService.loadUserByUsername("unactivated@company.com"));
    }

    @Test
    @DisplayName("CustomUserDetailsService returns UserPrincipal with cached authorities for active user")
    void testUserDetailsService_ActiveUser_ReturnsUserPrincipal() {
        User user = User.builder()
                .id(9L)
                .email("active_engineer@company.com")
                .password("encoded_secret")
                .role("EMPLOYEE")
                .systemRole("NONE")
                .employeeId(304L)
                .accountStatus("ACTIVE")
                .isActivated(true)
                .build();

        Employee emp = Employee.builder()
                .id(304L)
                .status("ACTIVE")
                .build();

        when(userRepository.findByEmail("active_engineer@company.com")).thenReturn(Optional.of(user));
        when(employeeRepository.findById(304L)).thenReturn(Optional.of(emp));
        when(employeeAuthorityRepository.findAuthoritiesByEmployeeId(304L)).thenReturn(List.of("VIEW_PAYSLIP", "APPLY_LEAVE"));

        UserDetails userDetails = userDetailsService.loadUserByUsername("active_engineer@company.com");

        assertNotNull(userDetails);
        assertTrue(userDetails instanceof UserPrincipal);
        UserPrincipal principal = (UserPrincipal) userDetails;

        assertEquals(9L, principal.getId());
        assertEquals("active_engineer@company.com", principal.getUsername());
        assertEquals("ACTIVE", principal.getAccountStatus());
        assertTrue(principal.getIsActivated());
        assertTrue(principal.isEnabled());
        assertTrue(principal.isAccountNonLocked());
        assertTrue(principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE")));
        assertTrue(principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("VIEW_PAYSLIP")));
        assertTrue(principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("APPLY_LEAVE")));
    }
}
