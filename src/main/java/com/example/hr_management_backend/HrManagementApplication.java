package com.example.hr_management_backend;

import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class HrManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrManagementApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedDefaultUsers(
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            // Seed Admin User
            if (!userRepository.existsByEmail("admin@company.com")) {
                Employee adminEmp = new Employee();
                adminEmp.setFirstName("System");
                adminEmp.setLastName("Admin");
                adminEmp.setEmail("admin@company.com");
                adminEmp.setDepartment("Management");
                adminEmp.setRole("ADMIN");
                Employee savedEmp = employeeRepository.save(adminEmp);

                User adminUser = User.builder()
                        .email("admin@company.com")
                        .password(passwordEncoder.encode("Password123!"))
                        .role("ADMIN")
                        .employeeId(savedEmp.getId())
                        .build();
                userRepository.save(adminUser);
            }

            // Seed Employee User
            if (!userRepository.existsByEmail("john.doe@company.com")) {
                Employee emp = new Employee();
                emp.setFirstName("John");
                emp.setLastName("Doe");
                emp.setEmail("john.doe@company.com");
                emp.setDepartment("Engineering");
                emp.setRole("EMPLOYEE");
                Employee savedEmp = employeeRepository.save(emp);

                User empUser = User.builder()
                        .email("john.doe@company.com")
                        .password(passwordEncoder.encode("Employee123!"))
                        .role("EMPLOYEE")
                        .employeeId(savedEmp.getId())
                        .build();
                userRepository.save(empUser);
            }

            // Seed Manager User
            if (!userRepository.existsByEmail("jane.smith@company.com")) {
                Employee mgr = new Employee();
                mgr.setFirstName("Jane");
                mgr.setLastName("Smith");
                mgr.setEmail("jane.smith@company.com");
                mgr.setDepartment("Human Resources");
                mgr.setRole("MANAGER");
                Employee savedEmp = employeeRepository.save(mgr);

                User mgrUser = User.builder()
                        .email("jane.smith@company.com")
                        .password(passwordEncoder.encode("Manager123!"))
                        .role("MANAGER")
                        .employeeId(savedEmp.getId())
                        .build();
                userRepository.save(mgrUser);
            }
        };
    }
}
