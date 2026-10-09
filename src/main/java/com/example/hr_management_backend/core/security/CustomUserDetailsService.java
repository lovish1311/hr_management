package com.example.hr_management_backend.core.security;

import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeAuthorityRepository;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final EmployeeAuthorityRepository employeeAuthorityRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + cleanEmail));

        java.util.Set<String> authorityNames = new java.util.LinkedHashSet<>();

        String roleName = user.getRole();
        if (roleName != null && !roleName.isBlank()) {
            if (!roleName.startsWith("ROLE_")) {
                roleName = "ROLE_" + roleName;
            }
            authorityNames.add(roleName);
        }

        String systemRole = user.getSystemRole();
        if ("SUPER_ADMIN".equalsIgnoreCase(systemRole) || "ROLE_SUPER_ADMIN".equalsIgnoreCase(user.getRole())) {
            authorityNames.add("ROLE_SUPER_ADMIN");
            authorityNames.add("ROLE_ADMIN");
            authorityNames.add("ROLE_HR");
            authorityNames.add("ROLE_MANAGER");
            authorityNames.add("ROLE_EMPLOYEE");
        } else if ("ADMIN".equalsIgnoreCase(systemRole) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole())) {
            authorityNames.add("ROLE_ADMIN");
            authorityNames.add("ROLE_HR");
            authorityNames.add("ROLE_MANAGER");
        }

        Long empId = user.getEmployeeId();
        if (empId == null) {
            empId = employeeRepository.findByEmail(email).map(Employee::getId).orElse(null);
        }

        if (empId != null) {
            List<String> authorities = employeeAuthorityRepository.findAuthoritiesByEmployeeId(empId);
            for (String auth : authorities) {
                if (auth != null && !auth.isBlank()) {
                    authorityNames.add(auth.trim().toUpperCase());
                }
            }
        }

        List<GrantedAuthority> grantedAuthorities = authorityNames.stream()
                .map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a))
                .toList();

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                grantedAuthorities
        );
    }
}
