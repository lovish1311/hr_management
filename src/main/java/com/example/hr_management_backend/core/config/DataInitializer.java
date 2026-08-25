package com.example.hr_management_backend.core.config;

import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (employeeRepository.count() < 5) {
            log.info("Starting complete production data seeding (1 Admin, 1 HR, 3 Managers, 10 Employees)...");

            // 1. Super Admin
            Employee adminEmployee = createEmployee("Super", "Admin", "admin@company.com", "Executive", "System Admin", "SUPER_ADMIN", "EMP-001", null);
            createUser("admin@company.com", "admin123", "ROLE_SUPER_ADMIN", adminEmployee.getId());

            // 2. HR Admin
            Employee hrEmployee = createEmployee("Priya", "Sharma", "hr@company.com", "Human Resources", "HR Head", "HR", "EMP-002", null);
            createUser("hr@company.com", "hr123", "ROLE_HR", hrEmployee.getId());

            // 3. Managers
            Employee harshManager = createEmployee("Harsh", "Kaushal", "harsh.kaushal@company.com", "Engineering", "Engineering Lead", "MANAGER", "EMP-101", null);
            createUser("harsh.kaushal@company.com", "manager123", "ROLE_MANAGER", harshManager.getId());

            Employee rahulManager = createEmployee("Rahul", "Verma", "rahul.verma@company.com", "Product", "Product Manager", "MANAGER", "EMP-102", null);
            createUser("rahul.verma@company.com", "manager123", "ROLE_MANAGER", rahulManager.getId());

            Employee ananyaManager = createEmployee("Ananya", "Roy", "ananya.roy@company.com", "Design", "Design Lead", "MANAGER", "EMP-103", null);
            createUser("ananya.roy@company.com", "manager123", "ROLE_MANAGER", ananyaManager.getId());

            // 4. Employees assigned to Managers
            // Team 1: Reporting to Harsh Kaushal
            Employee lovish = createEmployee("Lovish", "Kumar", "lovish@company.com", "Engineering", "Senior Software Engineer", "EMPLOYEE", "EMP-201", harshManager);
            createUser("lovish@company.com", "user123", "ROLE_EMPLOYEE", lovish.getId());

            Employee vikram = createEmployee("Vikram", "Singh", "vikram@company.com", "Engineering", "Frontend Developer", "EMPLOYEE", "EMP-202", harshManager);
            createUser("vikram@company.com", "user123", "ROLE_EMPLOYEE", vikram.getId());

            Employee sneha = createEmployee("Sneha", "Patel", "sneha@company.com", "Engineering", "Backend Developer", "EMPLOYEE", "EMP-203", harshManager);
            createUser("sneha@company.com", "user123", "ROLE_EMPLOYEE", sneha.getId());

            // Team 2: Reporting to Rahul Verma
            Employee amit = createEmployee("Amit", "Sharma", "amit@company.com", "Product", "Product Analyst", "EMPLOYEE", "EMP-204", rahulManager);
            createUser("amit@company.com", "user123", "ROLE_EMPLOYEE", amit.getId());

            Employee neha = createEmployee("Neha", "Gupta", "neha@company.com", "Product", "QA Lead", "EMPLOYEE", "EMP-205", rahulManager);
            createUser("neha@company.com", "user123", "ROLE_EMPLOYEE", neha.getId());

            Employee pooja = createEmployee("Pooja", "Mehta", "pooja@company.com", "Product", "Technical Writer", "EMPLOYEE", "EMP-206", rahulManager);
            createUser("pooja@company.com", "user123", "ROLE_EMPLOYEE", pooja.getId());

            // Team 3: Reporting to Ananya Roy
            Employee rohan = createEmployee("Rohan", "Das", "rohan@company.com", "Design", "UI/UX Designer", "EMPLOYEE", "EMP-207", ananyaManager);
            createUser("rohan@company.com", "user123", "ROLE_EMPLOYEE", rohan.getId());

            Employee tanvi = createEmployee("Tanvi", "Kapoor", "tanvi@company.com", "Design", "Graphic Designer", "EMPLOYEE", "EMP-208", ananyaManager);
            createUser("tanvi@company.com", "user123", "ROLE_EMPLOYEE", tanvi.getId());

            Employee karan = createEmployee("Karan", "Joshi", "karan@company.com", "Design", "Motion Designer", "EMPLOYEE", "EMP-209", ananyaManager);
            createUser("karan@company.com", "user123", "ROLE_EMPLOYEE", karan.getId());

            Employee divya = createEmployee("Divya", "Nair", "divya@company.com", "Design", "Content Strategist", "EMPLOYEE", "EMP-210", ananyaManager);
            createUser("divya@company.com", "user123", "ROLE_EMPLOYEE", divya.getId());

            // 5. Seed Initial Leave Quotas & Sample Leave Requests
            List<Employee> allEmployees = List.of(lovish, vikram, sneha, amit, neha, pooja, rohan, tanvi, karan, divya);
            for (Employee emp : allEmployees) {
                if (leaveBalanceRepository.findByEmployeeIdAndYear(emp.getId(), 2026).isEmpty()) {
                    LeaveBalance balance = LeaveBalance.builder()
                            .employeeId(emp.getId())
                            .year(2026)
                            .casualLeaveQuota(12)
                            .casualLeaveUsed(1)
                            .sickLeaveQuota(10)
                            .sickLeaveUsed(2)
                            .earnedLeaveQuota(15)
                            .earnedLeaveUsed(3)
                            .build();
                    leaveBalanceRepository.save(balance);
                }
            }

            LeaveRequest sampleReq1 = LeaveRequest.builder()
                    .employeeId(lovish.getId())
                    .startDate(LocalDate.now().plusDays(2))
                    .endDate(LocalDate.now().plusDays(4))
                    .leaveType("CASUAL")
                    .totalDays(3)
                    .reason("Personal work & family commitment")
                    .status("PENDING")
                    .build();
            leaveRequestRepository.save(sampleReq1);

            LeaveRequest sampleReq2 = LeaveRequest.builder()
                    .employeeId(vikram.getId())
                    .startDate(LocalDate.now().plusDays(5))
                    .endDate(LocalDate.now().plusDays(6))
                    .leaveType("SICK")
                    .totalDays(2)
                    .reason("Dental procedure")
                    .status("PENDING")
                    .build();
            leaveRequestRepository.save(sampleReq2);

            log.info("Database successfully initialized with 1 Admin, 1 HR, 3 Managers, 10 Employees, Quotas & Requests!");
        } else {
            // Always ensure admin@company.com password is reset to admin123 on startup
            userRepository.findByEmail("admin@company.com").ifPresent(admin -> {
                admin.setPassword(passwordEncoder.encode("admin123"));
                userRepository.save(admin);
            });
        }
    }

    private Employee createEmployee(String firstName, String lastName, String email, String dept, String designation, String role, String code, Employee manager) {
        return employeeRepository.findByEmail(email).orElseGet(() -> {
            Employee emp = Employee.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .department(dept)
                    .designation(designation)
                    .role(role)
                    .employeeCode(code)
                    .joiningDate(LocalDate.of(2024, 1, 15))
                    .employmentType("FULL_TIME")
                    .status("ACTIVE")
                    .phoneNumber("+91 98765 43210")
                    .address("Tech Park Road, City")
                    .emergencyContactName("Family Contact")
                    .emergencyContactPhone("+91 98765 00000")
                    .manager(manager)
                    .build();
            return employeeRepository.save(emp);
        });
    }

    private void createUser(String email, String password, String role, Long employeeId) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEmployeeId(employeeId);
        userRepository.save(user);
    }
}
