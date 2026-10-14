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

        // FAST PATH: Zero duplicate DB queries - extract pre-loaded & verified identity directly from UserPrincipal
        Object principalObj = authentication.getPrincipal();
        Long userId = null;
        String userEmail = cleanEmail;
        String userRole = "EMPLOYEE";
        String systemRole = "NONE";
        Long employeeId = null;
        java.util.List<String> authorityList = new java.util.ArrayList<>();

        if (principalObj instanceof com.example.hr_management_backend.core.security.UserPrincipal principal) {
            userId = principal.getId();
            userEmail = principal.getEmail();
            userRole = principal.getRole();
            systemRole = principal.getSystemRole();
            employeeId = principal.getEmployeeId();
            authorityList = principal.getAuthorities() != null
                    ? principal.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).toList()
                    : java.util.Collections.emptyList();
        } else {
            // Resilient fallback for test mocks or alternative authentication providers
            User user = userRepository.findByEmail(cleanEmail)
                    .orElseThrow(() -> new com.example.hr_management_backend.core.exception.ResourceNotFoundException("User not found: " + cleanEmail));
            userId = user.getId();
            userEmail = user.getEmail();
            userRole = user.getRole();
            systemRole = user.getSystemRole();
            employeeId = user.getEmployeeId();
            if (authentication.getAuthorities() != null) {
                authorityList = authentication.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
            }
        }

        return LoginResponse.builder()
                .token(jwt)
                .type("Bearer")
                .id(userId)
                .email(userEmail)
                .role(userRole)
                .systemRole(systemRole != null ? systemRole : "NONE")
                .employeeId(employeeId)
                .authorities(authorityList)
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
                .findFirstByActivationKeyHashAndExpiresAtAfter(keyHash, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired activation key. Please contact HR."));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new com.example.hr_management_backend.core.exception.ResourceNotFoundException("User account not found"));

        if (Boolean.TRUE.equals(token.getUsed()) || Boolean.TRUE.equals(user.getIsActivated()) || "ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
            log.info("[Account Activation] Activation attempted on already-active user: {}", user.getEmail());
            throw new IllegalStateException("Your account has already been activated! Please sign in with your email and password.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setIsActivated(true);
        user.setAccountStatus("ACTIVE");
        userRepository.save(user);

        token.setUsed(true);
        userActivationTokenRepository.save(token);
        userActivationTokenRepository.invalidateActiveTokensForUser(user.getId());

        log.info("[Account Activation] Account activated successfully for user: {}", user.getEmail());
        return user.getEmail();
    }

    @Override
    @Transactional(readOnly = true)
    public com.example.hr_management_backend.features.auth.dto.AccountStatusResponse getAccountStatus(String email) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        User user = userRepository.findByEmail(cleanEmail).orElse(null);

        if (user == null) {
            return com.example.hr_management_backend.features.auth.dto.AccountStatusResponse.builder()
                    .email(cleanEmail)
                    .status("NOT_FOUND")
                    .isActivated(false)
                    .message("No account found for this email. Please contact HR.")
                    .build();
        }

        String firstName = "";
        Long empId = user.getEmployeeId();
        if (empId != null) {
            firstName = employeeRepository.findById(empId)
                    .map(Employee::getFirstName)
                    .orElse("");
        }

        if ("DEACTIVATED".equalsIgnoreCase(user.getAccountStatus())) {
            return com.example.hr_management_backend.features.auth.dto.AccountStatusResponse.builder()
                    .email(cleanEmail)
                    .status("DEACTIVATED")
                    .isActivated(user.getIsActivated())
                    .firstName(firstName)
                    .message("This account has been deactivated. Please contact HR.")
                    .build();
        }

        if ("PENDING_ACTIVATION".equalsIgnoreCase(user.getAccountStatus()) || Boolean.FALSE.equals(user.getIsActivated())) {
            return com.example.hr_management_backend.features.auth.dto.AccountStatusResponse.builder()
                    .email(cleanEmail)
                    .status("PENDING_ACTIVATION")
                    .isActivated(false)
                    .firstName(firstName)
                    .message("Account pending activation. Enter your activation key to set your password.")
                    .build();
        }

        return com.example.hr_management_backend.features.auth.dto.AccountStatusResponse.builder()
                .email(cleanEmail)
                .status("ACTIVE")
                .isActivated(true)
                .firstName(firstName)
                .message("Account is active.")
                .build();
    }
}

