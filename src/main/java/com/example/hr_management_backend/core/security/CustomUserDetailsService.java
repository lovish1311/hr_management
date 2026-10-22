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
            empId = employeeRepository.findByEmail(cleanEmail).map(Employee::getId).orElse(null);
        }

        if (empId != null) {
            Employee emp = employeeRepository.findById(empId).orElse(null);
            if (emp != null) {
                if ("TERMINATED".equalsIgnoreCase(emp.getStatus())) {
                    if (!"DEACTIVATED".equalsIgnoreCase(user.getAccountStatus())) {
                        user.setAccountStatus("DEACTIVATED");
                        userRepository.save(user);
                    }
                    throw new org.springframework.security.authentication.DisabledException(
                            "Your employment has ended and this account has been deactivated. Please contact HR."
                    );
                } else if ("NOTICE_PERIOD".equalsIgnoreCase(emp.getStatus())
                        && emp.getRelievingDate() != null
                        && java.time.LocalDate.now().isAfter(emp.getRelievingDate())) {
                    emp.setStatus("TERMINATED");
                    employeeRepository.save(emp);
                    user.setAccountStatus("DEACTIVATED");
                    userRepository.save(user);
                    throw new org.springframework.security.authentication.DisabledException(
                            "Notice period concluded on " + emp.getRelievingDate() + ". Account has been deactivated."
                    );
                }
            }

            List<String> authorities = employeeAuthorityRepository.findAuthoritiesByEmployeeId(empId);
            for (String auth : authorities) {
                if (auth != null && !auth.isBlank()) {
                    authorityNames.add(auth.trim().toUpperCase());
                }
            }
        }

        if ("DEACTIVATED".equalsIgnoreCase(user.getAccountStatus())) {
            throw new org.springframework.security.authentication.DisabledException(
                    "Your account has been deactivated. Please contact HR."
            );
        }

        if ("PENDING_ACTIVATION".equalsIgnoreCase(user.getAccountStatus()) || Boolean.FALSE.equals(user.getIsActivated())) {
            throw new org.springframework.security.authentication.LockedException(
                    "Your account is pending activation. Please use your activation key to set your password."
            );
        }

        List<GrantedAuthority> grantedAuthorities = authorityNames.stream()
                .map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a))
                .toList();

        return UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password(user.getPassword())
                .role(user.getRole())
                .systemRole(systemRole)
                .employeeId(empId)
                .accountStatus(user.getAccountStatus())
                .isActivated(user.getIsActivated())
                .authorities(grantedAuthorities)
                .build();
    }

    @org.springframework.cache.annotation.Cacheable(value = "user_authorities", key = "#employeeId")
    public List<String> getDynamicAuthorities(Long employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        return employeeAuthorityRepository.findAuthoritiesByEmployeeId(employeeId);
    }
}
