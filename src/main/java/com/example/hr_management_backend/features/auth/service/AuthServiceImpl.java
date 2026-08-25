package com.example.hr_management_backend.features.auth.service;

import com.example.hr_management_backend.core.security.JwtUtils;
import com.example.hr_management_backend.features.auth.dto.LoginRequest;
import com.example.hr_management_backend.features.auth.dto.LoginResponse;
import com.example.hr_management_backend.features.auth.dto.RegisterRequest;
import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found: " + loginRequest.getEmail()));

        return LoginResponse.builder()
                .token(jwt)
                .type("Bearer")
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .employeeId(user.getEmployeeId())
                .build();
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Error: Email is already registered!");
        }

        Long employeeId = null;
        if (registerRequest.getFirstName() != null && registerRequest.getLastName() != null) {
            Employee employee = new Employee();
            employee.setFirstName(registerRequest.getFirstName());
            employee.setLastName(registerRequest.getLastName());
            employee.setEmail(registerRequest.getEmail());
            employee.setDepartment(registerRequest.getDepartment() != null ? registerRequest.getDepartment() : "General");
            employee.setRole(registerRequest.getRole() != null ? registerRequest.getRole() : "EMPLOYEE");
            Employee savedEmployee = employeeRepository.save(employee);
            employeeId = savedEmployee.getId();
        }

        String userRole = registerRequest.getRole() != null ? registerRequest.getRole() : "EMPLOYEE";

        User user = User.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(userRole)
                .employeeId(employeeId)
                .build();

        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest(registerRequest.getEmail(), registerRequest.getPassword());
        return login(loginRequest);
    }

    @Override
    public User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
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
}

