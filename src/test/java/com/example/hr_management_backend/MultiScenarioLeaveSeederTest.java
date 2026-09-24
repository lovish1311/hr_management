package com.example.hr_management_backend;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class MultiScenarioLeaveSeederTest {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    @DisplayName("SEED MULTI-SCENARIO LEAVE REQUESTS FOR MANUAL TESTING Across Employee, HR, and Admin Personas")
    void seedMultiScenarioLeaveRequests() {
        System.out.println("\n====================================================================");
        System.out.println("SEEDING 6 DISTINCT LEAVE REQUESTS FOR MANUAL UI TESTING");
        System.out.println("====================================================================\n");

        // 1. Resolve or create Lovish (Employee)
        Employee lovish = employeeRepository.findByEmail("lovish1311@company.com").orElseGet(() -> {
            Employee emp = Employee.builder()
                    .firstName("Lovish")
                    .lastName("Kumar")
                    .email("lovish1311@company.com")
                    .department("Engineering")
                    .role("EMPLOYEE")
                    .build();
            return employeeRepository.save(emp);
        });

        // 2. Resolve or create Jane Smith (HR Lead / Manager)
        Employee janeHr = employeeRepository.findByEmail("jane.smith@company.com").orElseGet(() -> {
            Employee emp = Employee.builder()
                    .firstName("Jane")
                    .lastName("Smith")
                    .email("jane.smith@company.com")
                    .department("Human Resources")
                    .role("MANAGER")
                    .build();
            return employeeRepository.save(emp);
        });

        // Link Jane as manager for Lovish
        lovish.setManager(janeHr);
        employeeRepository.save(lovish);

        LocalDate baseDate = LocalDate.now();

        // Request 1: Casual Leave (2 Days) -> Assigned to Manager for Approval
        LeaveRequest r1 = leaveService.applyForLeave(LeaveRequest.builder()
                .employeeId(lovish.getId())
                .leaveType("CASUAL")
                .startDate(baseDate.plusDays(3))
                .endDate(baseDate.plusDays(4))
                .startSession("Session 1")
                .endSession("Session 2")
                .reason("Personal trip & family gathering")
                .build());
        System.out.println("✅ Request 1 (Casual Leave, 2 Days) created -> ID: " + r1.getId() + ", Status: " + r1.getStatus());

        // Request 2: Sick Leave (1 Day) -> Prepared for Admin Rejection Test
        LeaveRequest r2 = leaveService.applyForLeave(LeaveRequest.builder()
                .employeeId(lovish.getId())
                .leaveType("SICK")
                .startDate(baseDate.plusDays(10))
                .endDate(baseDate.plusDays(10))
                .startSession("Session 1")
                .endSession("Session 2")
                .reason("Severe fever and doctor advice")
                .build());
        System.out.println("✅ Request 2 (Sick Leave, 1 Day) created -> ID: " + r2.getId() + ", Status: " + r2.getStatus());

        // Request 3: Short Break #1 (Time-based, Free Quota 1)
        LeaveRequest r3 = leaveService.applyForLeave(LeaveRequest.builder()
                .employeeId(lovish.getId())
                .leaveType("SHORT_BREAK")
                .startDate(baseDate.plusDays(12))
                .endDate(baseDate.plusDays(12))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .reason("Bank passbook verification")
                .build());
        System.out.println("✅ Request 3 (Short Break 1) created -> ID: " + r3.getId() + ", Status: " + r3.getStatus());

        // Request 4: Early Out #2 (Time-based, Free Quota 2)
        LeaveRequest r4 = leaveService.applyForLeave(LeaveRequest.builder()
                .employeeId(lovish.getId())
                .leaveType("EARLY_OUT")
                .startDate(baseDate.plusDays(14))
                .endDate(baseDate.plusDays(14))
                .startTime(LocalTime.of(16, 0))
                .endTime(LocalTime.of(17, 30))
                .reason("Dentist appointment")
                .build());
        System.out.println("✅ Request 4 (Early Out 2) created -> ID: " + r4.getId() + ", Status: " + r4.getStatus());

        // Request 5: Short Break #3 (Quota Exceeded -> 0.5 Day Penalty Flagged!)
        LeaveRequest r5 = leaveService.applyForLeave(LeaveRequest.builder()
                .employeeId(lovish.getId())
                .leaveType("SHORT_BREAK")
                .startDate(baseDate.plusDays(16))
                .endDate(baseDate.plusDays(16))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(15, 0))
                .reason("Emergency home parcel delivery")
                .build());
        System.out.println("✅ Request 5 (Short Break 3 - Quota Exceeded) created -> ID: " + r5.getId() + ", Reason: " + r5.getReason());

        // Request 6: Work From Home (1 Day) -> Pending HR Review
        LeaveRequest r6 = leaveService.applyForLeave(LeaveRequest.builder()
                .employeeId(lovish.getId())
                .leaveType("WORK_FROM_HOME")
                .startDate(baseDate.plusDays(20))
                .endDate(baseDate.plusDays(20))
                .startSession("Session 1")
                .endSession("Session 2")
                .reason("Wi-Fi installation at home")
                .build());
        System.out.println("✅ Request 6 (Work From Home) created -> ID: " + r6.getId() + ", Status: " + r6.getStatus());

        assertNotNull(r1.getId());
        assertNotNull(r6.getId());

        System.out.println("\n====================================================================");
        System.out.println("🎉 ALL 6 MULTI-SCENARIO LEAVE REQUESTS SEEDED SUCCESSFULLY!");
        System.out.println("====================================================================\n");
    }
}
