package com.example.hr_management_backend.features.leaves;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class LeaveConcurrencyTest {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee testEmployee;
    private Employee testManager;

    @BeforeEach
    void setUp() {
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();

        if (employeeRepository.findByEmail("mgr_conc@test.com").isEmpty()) {
            testManager = employeeRepository.save(Employee.builder()
                    .email("mgr_conc@test.com")
                    .firstName("Manager")
                    .lastName("Conc")
                    .role("MANAGER")
                    .employeeCode("MGR_CONC")
                    .build());
        } else {
            testManager = employeeRepository.findByEmail("mgr_conc@test.com").get();
        }

        if (employeeRepository.findByEmail("emp_conc@test.com").isEmpty()) {
            testEmployee = employeeRepository.save(Employee.builder()
                    .email("emp_conc@test.com")
                    .firstName("Emp")
                    .lastName("Conc")
                    .role("EMPLOYEE")
                    .employeeCode("EMP_CONC")
                    .manager(testManager)
                    .build());
        } else {
            testEmployee = employeeRepository.findByEmail("emp_conc@test.com").get();
        }
    }

    @Test
    @DisplayName("Concurrent leave applications cannot bypass quota (exactly 1 succeeds if applying for max remaining)")
    void testConcurrentSubmissionQuotaBypass() throws InterruptedException {
        int year = LocalDate.now().getYear();
        
        // Initialize balance to exactly 10 casual leaves
        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .casualLeaveQuota(10.0)
                .casualLeaveUsed(0.0)
                .build();
        leaveBalanceRepository.save(balance);

        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger exceptionCount = new AtomicInteger();

        Runnable task = () -> {
            try {
                latch.await(); // Synchronize start
                LeaveRequest request = LeaveRequest.builder()
                        .employeeId(testEmployee.getId())
                        .leaveType("CASUAL")
                        .startDate(LocalDate.now().plusDays(1))
                        .endDate(LocalDate.now().plusDays(10)) // 10 days
                        .build();
                leaveService.applyForLeave(request, testEmployee.getEmail());
                successCount.incrementAndGet();
            } catch (Exception e) {
                exceptionCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(task);
        }

        latch.countDown(); // Let all threads run concurrently
        doneLatch.await();

        // Exactly 1 thread should succeed, and 1 should fail with IllegalStateException (Insufficient balance)
        assertEquals(1, successCount.get(), "Only one request should succeed due to quota.");
        assertEquals(1, exceptionCount.get(), "One request should fail due to insufficient balance.");

        LeaveBalance updatedBalance = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), year);
        assertEquals(0.0, updatedBalance.getCasualLeaveRemaining(), "Available balance should be exactly 0.");
        
        executorService.shutdown();
    }

    @Test
    @DisplayName("Concurrent rejection cannot double-refund (one succeeds, one hits ObjectOptimisticLockingFailureException, balance refunded once)")
    void testConcurrentRejectionDoubleRefund() throws InterruptedException {
        int year = LocalDate.now().getYear();
        
        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .casualLeaveQuota(10.0)
                .casualLeaveUsed(5.0) // 5 days used
                .build();
        leaveBalanceRepository.save(balance);

        LeaveRequest request = leaveRequestRepository.save(LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("CASUAL")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5)) // 5 days
                .status("APPROVED") // Initially APPROVED
                .totalDays(5.0)
                .build());

        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger exceptionCount = new AtomicInteger();

        Runnable task = () -> {
            try {
                latch.await();
                // Manager rejects the request concurrently
                leaveService.updateStatus(request.getId(), "REJECTED", "Concurrent rejection", testManager.getEmail());
                successCount.incrementAndGet();
            } catch (Exception e) {
                exceptionCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(task);
        }

        latch.countDown();
        doneLatch.await();

        assertEquals(1, successCount.get(), "Only one rejection should succeed.");
        assertEquals(1, exceptionCount.get(), "One rejection should hit optimistic locking exception.");

        LeaveBalance updatedBalance = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), year);
        // Balance should be refunded by exactly 5 days (from 5 used to 0 used)
        assertEquals(0.0, updatedBalance.getCasualLeaveUsed(), "Balance should be refunded exactly once.");
        
        executorService.shutdown();
    }
}
