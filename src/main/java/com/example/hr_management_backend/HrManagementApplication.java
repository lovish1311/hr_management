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
            // Seed Employee User (Lovish)
            if (!userRepository.existsByEmail("lovish@company.com")) {
                Employee lovishEmp = new Employee();
                lovishEmp.setFirstName("Lovish");
                lovishEmp.setLastName("Kumar");
                lovishEmp.setEmail("lovish@company.com");
                lovishEmp.setDepartment("Engineering");
                lovishEmp.setRole("EMPLOYEE");
                Employee savedLovish = employeeRepository.save(lovishEmp);

                User lovishUser = User.builder()
                        .email("lovish@company.com")
                        .password(passwordEncoder.encode("user123"))
                        .role("EMPLOYEE")
                        .employeeId(savedLovish.getId())
                        .build();
                userRepository.save(lovishUser);
            }

            // Seed HR Lead User (Aadisha / HR)
            if (!userRepository.existsByEmail("hr@company.com")) {
                Employee hrEmp = new Employee();
                hrEmp.setFirstName("Aadisha");
                hrEmp.setLastName("HR");
                hrEmp.setEmail("hr@company.com");
                hrEmp.setDepartment("Human Resources");
                hrEmp.setRole("HR");
                Employee savedHr = employeeRepository.save(hrEmp);

                User hrUser = User.builder()
                        .email("hr@company.com")
                        .password(passwordEncoder.encode("hr123"))
                        .role("HR")
                        .employeeId(savedHr.getId())
                        .build();
                userRepository.save(hrUser);

                // Assign HR as Manager for Lovish
                userRepository.findByEmail("lovish@company.com").ifPresent(u -> {
                    employeeRepository.findById(u.getEmployeeId()).ifPresent(e -> {
                        e.setManager(savedHr);
                        employeeRepository.save(e);
                    });
                });
            }

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
                        .password(passwordEncoder.encode("admin123"))
                        .role("ADMIN")
                        .employeeId(savedEmp.getId())
                        .build();
                userRepository.save(adminUser);
            }
        };
    }
}
