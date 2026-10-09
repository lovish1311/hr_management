package com.example.hr_management_backend.features.auth.service;

import com.example.hr_management_backend.core.security.JwtUtils;
import com.example.hr_management_backend.features.auth.dto.LoginRequest;
import com.example.hr_management_backend.features.auth.dto.LoginResponse;
import com.example.hr_management_backend.features.auth.dto.RegisterRequest;
import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.auth.model.PasswordResetToken;
import com.example.hr_management_backend.features.auth.model.UserActivationToken;
import com.example.hr_management_backend.features.auth.repository.PasswordResetTokenRepository;
import com.example.hr_management_backend.features.auth.repository.UserActivationTokenRepository;
import com.example.hr_management_backend.features.email.service.EmailOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final com.example.hr_management_backend.features.employees.repository.EmployeeAuthorityRepository employeeAuthorityRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserActivationTokenRepository userActivationTokenRepository;
    private final EmailOutboxService emailOutboxService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error computing SHA-256 hash", e);
        }
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String cleanEmail = loginRequest.getEmail() != null ? loginRequest.getEmail().trim().toLowerCase() : "";
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        cleanEmail,
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + cleanEmail));

        Long empId = user.getEmployeeId();
        if (empId == null) {
            empId = employeeRepository.findByEmail(user.getEmail()).map(com.example.hr_management_backend.features.employees.model.Employee::getId).orElse(null);
        }

        java.util.Set<String> authoritySet = new java.util.LinkedHashSet<>();
        if (empId != null) {
            authoritySet.addAll(employeeAuthorityRepository.findAuthoritiesByEmployeeId(empId));
        }

        String systemRole = user.getSystemRole();
        if (systemRole == null || systemRole.isBlank()) {
            systemRole = "NONE";
        }
        if ("SUPER_ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_SUPER_ADMIN".equalsIgnoreCase(user.getRole()) || "SUPER_ADMIN".equalsIgnoreCase(systemRole)) {
            systemRole = "SUPER_ADMIN";
            authoritySet.add("ROLE_SUPER_ADMIN");
            authoritySet.add("ROLE_ADMIN");
        } else if ("ADMIN".equalsIgnoreCase(systemRole) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole())) {
            authoritySet.add("ROLE_ADMIN");
        }

        return LoginResponse.builder()
                .token(jwt)
                .type("Bearer")
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .systemRole(systemRole)
                .employeeId(empId)
                .authorities(new java.util.ArrayList<>(authoritySet))
                .build();
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest registerRequest) {
        String cleanEmail = registerRequest.getEmail() != null ? registerRequest.getEmail().trim().toLowerCase() : "";
        if (userRepository.existsByEmail(cleanEmail)) {
            throw new RuntimeException("Error: Email is already registered!");
        }

        Long employeeId = null;
        if (registerRequest.getFirstName() != null && registerRequest.getLastName() != null) {
            Employee employee = new Employee();
            employee.setFirstName(registerRequest.getFirstName());
            employee.setLastName(registerRequest.getLastName());
            employee.setEmail(cleanEmail);
            employee.setDepartment(registerRequest.getDepartment() != null ? registerRequest.getDepartment() : "General");
            employee.setRole(registerRequest.getRole() != null ? registerRequest.getRole() : "EMPLOYEE");
            Employee savedEmployee = employeeRepository.save(employee);
            employeeId = savedEmployee.getId();
        }

        String userRole = registerRequest.getRole() != null ? registerRequest.getRole() : "EMPLOYEE";

        User user = User.builder()
                .email(cleanEmail)
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(userRole)
                .employeeId(employeeId)
                .build();

        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest(cleanEmail, registerRequest.getPassword());
        return login(loginRequest);
    }

    @Override
    public User getCurrentUser(String email) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        return userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + cleanEmail));
    }

    @Override
    @Transactional
    public User updateUserRole(Long userId, String newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        String formattedRole = newRole.startsWith("ROLE_") ? newRole : "ROLE_" + newRole;
        user.setRole(formattedRole);

        if (user.getEmployeeId() != null) {
            employeeRepository.findById(user.getEmployeeId()).ifPresent(emp -> {
                emp.setRole(formattedRole.replace("ROLE_", ""));
                employeeRepository.save(emp);
            });
        }

        return userRepository.save(user);
    }

    @Override
    public void logout(String token) {
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        java.util.Optional<User> userOpt = userRepository.findByEmail(cleanEmail);
        if (userOpt.isEmpty()) {
            // Bank-grade Anti-Enumeration: Avoid leaking registered email existence to clients
            log.warn("[Password Reset] Request received for unregistered email: {}", cleanEmail);
            return;
        }

        // Anti-spam & Cooldown check: 60 seconds minimum between OTP requests
        var recentTokenOpt = passwordResetTokenRepository.findTopByEmailOrderByCreatedAtDesc(cleanEmail);
        if (recentTokenOpt.isPresent() && recentTokenOpt.get().getCreatedAt() != null
                && recentTokenOpt.get().getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(60))) {
            log.warn("[Password Reset Cooldown] Request throttled for email: {} (requested within last 60 seconds)", cleanEmail);
            return; // Anti-enumeration: returns identically without alerting attacker
        }

        // Invalidate all previous unexpired tokens for this email to prevent token stacking
        passwordResetTokenRepository.invalidateActiveTokensForEmail(cleanEmail);

        User user = userOpt.get();
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        String tokenHash = sha256(otp);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .email(cleanEmail)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .used(false)
                .attemptCount(0)
                .build();
        passwordResetTokenRepository.save(resetToken);

        String empName = employeeRepository.findByEmail(cleanEmail)
                .map(com.example.hr_management_backend.features.employees.model.Employee::getName)
                .orElse("Team Member");

        emailOutboxService.sendPasswordResetOtp(cleanEmail, empName, otp);
        log.info("[Password Reset] Dispatched OTP for user {}", cleanEmail);
    }

    @Override
    @Transactional
    public void resetPassword(String email, String token, String newPassword) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        String cleanToken = token != null ? token.trim() : "";
        String tokenHash = sha256(cleanToken);

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findFirstByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(cleanEmail, LocalDateTime.now())
                .orElseThrow(() -> new RuntimeException("Invalid or expired verification code. Please request a new code."));

        // Persist attempt count immediately in an isolated transaction so it is never rolled back
        passwordResetTokenRepository.incrementAttemptCount(resetToken.getId());
        int currentAttempts = (resetToken.getAttemptCount() != null ? resetToken.getAttemptCount() : 0) + 1;

        if (!resetToken.getTokenHash().equals(tokenHash)) {
            if (currentAttempts >= 5) {
                passwordResetTokenRepository.markTokenUsed(resetToken.getId());
                log.warn("[Password Reset Security] Max attempts exceeded for email: {}. Token invalidated.", cleanEmail);
                throw new RuntimeException("Too many incorrect attempts. This verification code has been deactivated for security. Please request a new code.");
            }
            int remaining = 5 - currentAttempts;
            throw new RuntimeException("Invalid verification code. " + remaining + " attempt(s) remaining.");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User account not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        passwordResetTokenRepository.markTokenUsed(resetToken.getId());
        log.info("[Password Reset] Password reset successfully for user: {}", cleanEmail);
    }

    @Override
    @Transactional
    public String activateAccount(String activationKey, String newPassword) {
        String cleanKey = activationKey != null ? activationKey.trim() : "";
        String keyHash = sha256(cleanKey);

        UserActivationToken token = userActivationTokenRepository
                .findFirstByActivationKeyHashAndUsedFalseAndExpiresAtAfter(keyHash, LocalDateTime.now())
                .orElseThrow(() -> new RuntimeException("Invalid or expired activation key. Please contact HR."));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new RuntimeException("User account not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        userActivationTokenRepository.save(token);
        log.info("[Account Activation] Account activated successfully for user: {}", user.getEmail());
        return user.getEmail();
    }
}

