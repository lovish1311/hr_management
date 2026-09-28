package com.example.hr_management_backend.features.leaves;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.controller.LeaveController;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LeaveSecurityTest {

    @Autowired
    private LeaveController leaveController;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    private Employee regularEmployee1;
    private Employee regularEmployee2;
    private Employee manager;
    private Employee hrAdmin;

    @BeforeEach
    void setUp() {
        hrAdmin = employeeRepository.findByEmail("hr@company.com").orElseGet(() -> {
            return employeeRepository.save(Employee.builder().email("hr@company.com").firstName("HR").lastName("Admin").role("HR").employeeCode("HR01").build());
        });

        manager = employeeRepository.findByEmail("manager@company.com").orElseGet(() -> {
            return employeeRepository.save(Employee.builder().email("manager@company.com").firstName("Manager").lastName("User").role("MANAGER").employeeCode("MGR01").build());
        });

        regularEmployee1 = employeeRepository.findByEmail("emp1@company.com").orElseGet(() -> {
            return employeeRepository.save(Employee.builder().email("emp1@company.com").firstName("Emp").lastName("One").role("EMPLOYEE").employeeCode("EMP01").manager(manager).build());
        });

        regularEmployee2 = employeeRepository.findByEmail("emp2@company.com").orElseGet(() -> {
            return employeeRepository.save(Employee.builder().email("emp2@company.com").firstName("Emp").lastName("Two").role("EMPLOYEE").employeeCode("EMP02").manager(manager).build());
        });
    }

    @Test
    @WithMockUser(username = "emp1@company.com", roles = {"EMPLOYEE"})
    @DisplayName("Employee A cannot impersonate Employee B when applying for leave")
    void testEmployeeCannotImpersonate() {
        LeaveRequest request = LeaveRequest.builder()
                .employeeId(regularEmployee2.getId()) // TRYING TO IMPERSONATE EMP2
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(6))
                .reason("Impersonation test")
                .leaveType("CASUAL")
                .build();

        leaveController.applyForLeave(request);

        // Verify the created leave request was assigned to EMP1 (the authenticated user) and NOT EMP2
        LeaveRequest saved = leaveRequestRepository.findAll().stream()
                .filter(r -> "Impersonation test".equals(r.getReason()))
                .findFirst().orElseThrow();
                
        assertEquals(regularEmployee1.getId(), saved.getEmployeeId(), "The leave request must be assigned to the authenticated user, not the payload ID");
    }

    @Test
    @WithMockUser(username = "emp1@company.com", roles = {"EMPLOYEE"})
    @DisplayName("Employee cannot self-approve their own leave")
    void testEmployeeCannotSelfApprove() {
        LeaveRequest request = leaveRequestRepository.save(LeaveRequest.builder()
                .employeeId(regularEmployee1.getId())
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(11))
                .status("PENDING")
                .build());

        Map<String, String> payload = new HashMap<>();
        payload.put("status", "APPROVED");

        assertThrows(AccessDeniedException.class, () -> {
            leaveController.updateStatus(request.getId(), payload);
        });
    }

    @Test
    @WithMockUser(username = "manager@company.com", roles = {"MANAGER"})
    @DisplayName("Manager can approve their assigned employee's leave")
    void testManagerCanApprove() {
        LeaveRequest request = leaveRequestRepository.save(LeaveRequest.builder()
                .employeeId(regularEmployee1.getId())
                .startDate(LocalDate.now().plusDays(15))
                .endDate(LocalDate.now().plusDays(16))
                .status("PENDING")
                .build());

        Map<String, String> payload = new HashMap<>();
        payload.put("status", "APPROVED");

        leaveController.updateStatus(request.getId(), payload);
                
        assertEquals("APPROVED", leaveRequestRepository.findById(request.getId()).get().getStatus());
    }
    
    @Test
    @WithMockUser(username = "emp2@company.com", roles = {"EMPLOYEE"})
    @DisplayName("Employee cannot approve another employee's leave")
    void testEmployeeCannotApproveOther() {
        LeaveRequest request = leaveRequestRepository.save(LeaveRequest.builder()
                .employeeId(regularEmployee1.getId())
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(21))
                .status("PENDING")
                .build());

        Map<String, String> payload = new HashMap<>();
        payload.put("status", "APPROVED");

        assertThrows(AccessDeniedException.class, () -> {
            leaveController.updateStatus(request.getId(), payload);
        });
    }

    @Test
    @WithMockUser(username = "emp1@company.com", roles = {"EMPLOYEE"})
    @DisplayName("Employee cannot call destructive endpoints")
    void testEmployeeCannotCallClear() {
        assertThrows(AccessDeniedException.class, () -> {
            leaveController.clearAllLeaveData();
        });
                
        assertThrows(AccessDeniedException.class, () -> {
            leaveController.clearEmployeeLeaveData(regularEmployee1.getId());
        });
    }
}
