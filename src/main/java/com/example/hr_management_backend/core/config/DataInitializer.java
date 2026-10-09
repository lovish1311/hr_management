package com.example.hr_management_backend.core.config;

import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.example.hr_management_backend.features.payroll.service.PayrollService payrollService;

    @Override
    public void run(String... args) throws Exception {
        log.info("Ensuring complete production data seeding (1 Admin, 1 HR: Aadisha Dhullar, 3 Managers, 28 Excel Employees)...");

            // 1. Super Admin: Bust the rule that super admins can't have their own ID!
            // Super Admin has its own employee entity (EMP-001) alongside system role SUPER_ADMIN
            Employee adminEmployee = createEmployee("System", "Administrator", "admin@company.com", "Executive", "System Administrator", "SUPER_ADMIN", "EMP-001", "System Admin", null, false, "+91 98123 00001", "1990-01-01", "Other", "HQ, Sector 17, Chandigarh");
            adminEmployee.setSystemRole("SUPER_ADMIN");
            adminEmployee = employeeRepository.save(adminEmployee);
            createUser("admin@company.com", "admin123", "ROLE_SUPER_ADMIN", "SUPER_ADMIN", adminEmployee.getId());

            // 2. HR Head: Aadisha Dhullar (Employee entity)
            Employee hrEmployee = createEmployee("Aadisha", "Dhullar", "hr@company.com", "Human Resources", "HR Lead", "HR", "EMP-002", "Aadisha Dhullar", null, false, "+91 98123 00002", "1994-06-18", "Female", "Sector 17, Chandigarh");
            createUser("hr@company.com", "hr123", "ROLE_HR", "NONE", hrEmployee.getId());

            // 3. Managers (isAttendanceTracked = false)
            Employee harshManager = createEmployee("Harsh", "Kaushal", "harsh.kaushal@company.com", "Engineering", "Engineering Lead", "MANAGER", "EMP-101", "Harsh Kaushal", null, false, "+91 98123 00101", "1989-03-25", "Male", "Phase 8, Mohali");
            createUser("harsh.kaushal@company.com", "manager123", "ROLE_MANAGER", "NONE", harshManager.getId());

            Employee naveenManager = createEmployee("Naveen Chandra", "Tiwari", "naveen.tiwari@company.com", "Product", "Product Manager", "MANAGER", "EMP-102", "Naveen Chandra Tiwari", null, false, "+91 98123 00102", "1988-11-12", "Male", "IT Park, Chandigarh");
            createUser("naveen.tiwari@company.com", "manager123", "ROLE_MANAGER", "NONE", naveenManager.getId());

            Employee ankeshManager = createEmployee("Ankesh", "Verma", "ankesh.verma@company.com", "Sales & Marketing", "Sales Director", "MANAGER", "EMP-103", "Ankesh Verma", null, false, "+91 98123 00103", "1990-07-08", "Male", "Civil Lines, Jaipur");
            createUser("ankesh.verma@company.com", "manager123", "ROLE_MANAGER", "NONE", ankeshManager.getId());

            // 4. Employees (All 29 Real Excel Employees - isAttendanceTracked = true)
            // Engineering Team (Harsh Kaushal)
            Employee ishu = createEmployee("Ishu", "Saini", "ishu.saini@company.com", "Engineering", "Senior Software Developer", "EMPLOYEE", "EMP-201", "ishu Saini", harshManager, true, "+91 98123 45201", "1997-04-12", "Male", "Sector 34, Chandigarh");
            Employee lovish = createEmployee("Lovish", "Kumar", "lovish@company.com", "Engineering", "Senior Software Developer", "EMPLOYEE", "EMP-202", "Lovish", harshManager, true, "+91 98123 45202", "1996-08-19", "Male", "Phase 7, Mohali");
            Employee abhishekG = createEmployee("Abhishek", "Gaur", "abhishek.g@company.com", "Engineering", "Frontend Developer", "EMPLOYEE", "EMP-203", "AbhishekG", harshManager, true, "+91 98123 45203", "1998-02-14", "Male", "Sector 22, Chandigarh");
            Employee abhinav = createEmployee("Abhinav", "Singh", "abhinav@company.com", "Engineering", "Backend Developer", "EMPLOYEE", "EMP-204", "Abhinav", harshManager, true, "+91 98123 45204", "1997-11-05", "Male", "Zirakpur, Punjab");
            Employee gurkirat = createEmployee("Gurkirat", "Singh", "gurkirat@company.com", "Engineering", "Tech Lead", "EMPLOYEE", "EMP-205", "Gurkirat", harshManager, true, "+91 98123 45205", "1995-09-30", "Male", "Phase 3B2, Mohali");
            Employee ashish = createEmployee("Ashish", "Chauhan", "ashish@company.com", "Engineering", "QA Engineer", "EMPLOYEE", "EMP-206", "Ashish Chauhan", harshManager, true, "+91 98123 45206", "1999-01-22", "Male", "Sector 70, Mohali");
            Employee aniket = createEmployee("Aniket", "Sharma", "aniket@company.com", "Engineering", "Software Engineer", "EMPLOYEE", "EMP-208", "Aniket Sharma", harshManager, true, "+91 98123 45208", "1998-10-18", "Male", "Sector 11, Panchkula");
            Employee himanshu = createEmployee("Himanshu", "Rana", "himanshu.rana@company.com", "Engineering", "DevOps Engineer", "EMPLOYEE", "EMP-229", "Himanshu Rana", harshManager, true, "+91 98123 45229", "1996-05-14", "Male", "Phase 7, Mohali");

            // Product & Operations Team (Naveen Chandra Tiwari)
            Employee kuldeep = createEmployee("Kuldeep", "Singh", "kuldeep@company.com", "Product", "Technical Writer", "EMPLOYEE", "EMP-211", "Kuldeep", naveenManager, true, "+91 98123 45211", "1994-07-21", "Male", "Kharar, Punjab");
            Employee abhishekT = createEmployee("Abhishek", "Thakur", "abhishek.thakur@company.com", "Product", "Business Analyst", "EMPLOYEE", "EMP-212", "Abhishek Thakur", naveenManager, true, "+91 98123 45212", "1997-05-09", "Male", "Sector 35, Chandigarh");
            Employee parav = createEmployee("Parav", "Taneja", "parav@company.com", "Product", "QA Engineer", "EMPLOYEE", "EMP-213", "Parav Taneja", naveenManager, true, "+91 98123 45213", "1999-09-17", "Male", "Phase 5, Mohali");
            Employee sandeep = createEmployee("Sandeep", "Gill", "sandeep.gill@company.com", "Operations", "Operations Analyst", "EMPLOYEE", "EMP-230", "Sandeep Gill", naveenManager, true, "+91 98123 45230", "1995-08-21", "Male", "IT Park, Chandigarh");

            // Sales, Design & Marketing Team (Ankesh Verma)
            Employee anshu = createEmployee("Anshu", "Sharma", "anshu@company.com", "Marketing", "Content Writer", "EMPLOYEE", "EMP-214", "Anshu", ankeshManager, true, "+91 98123 45214", "1998-08-25", "Female", "Sector 46, Chandigarh");
            Employee nisha = createEmployee("Nisha", "Verma", "nisha@company.com", "Marketing", "Digital Marketing Specialist", "EMPLOYEE", "EMP-215", "Nisha", ankeshManager, true, "+91 98123 45215", "1997-02-11", "Female", "Sector 20, Panchkula");
            Employee mehak = createEmployee("Mehak", "Gupta", "mehak@company.com", "Design", "UI/UX Designer", "EMPLOYEE", "EMP-216", "Mehak", ankeshManager, true, "+91 98123 45216", "1996-10-31", "Female", "Sector 18, Chandigarh");
            Employee harleen = createEmployee("Harleen", "Kaur", "harleen@company.com", "Design", "Graphic Designer", "EMPLOYEE", "EMP-217", "Harleen", ankeshManager, true, "+91 98123 45217", "1998-04-05", "Female", "Phase 9, Mohali");
            Employee jyoti = createEmployee("Jyoti", "Rani", "jyoti@company.com", "Operations", "Operations Executive", "EMPLOYEE", "EMP-218", "Jyoti", ankeshManager, true, "+91 98123 45218", "1995-01-19", "Female", "Sector 38, Chandigarh");
            Employee sadham = createEmployee("Sadham", "Hussain", "sadham@company.com", "Sales", "BDE", "EMPLOYEE", "EMP-219", "Sadham", ankeshManager, true, "+91 98123 45219", "1997-06-30", "Male", "Sector 47, Chandigarh");
            Employee vishali = createEmployee("Vishali", "Devi", "vishali@company.com", "Sales", "BDM", "EMPLOYEE", "EMP-220", "Vishali", ankeshManager, true, "+91 98123 45220", "1996-12-14", "Female", "Phase 10, Mohali");
            Employee anjali = createEmployee("Anjali", "Kumari", "anjali@company.com", "Design", "Motion Artist", "EMPLOYEE", "EMP-221", "AnjaliK", ankeshManager, true, "+91 98123 45221", "1998-07-28", "Female", "Sector 21, Chandigarh");
            Employee chanda = createEmployee("Chanda", "Rani", "chanda@company.com", "Design", "UI/UX Designer", "EMPLOYEE", "EMP-222", "Chanda", ankeshManager, true, "+91 98123 45222", "1997-03-03", "Female", "Sector 19, Chandigarh");
            Employee palak = createEmployee("Palak", "Sharma", "palak@company.com", "Marketing", "SEO Analyst", "EMPLOYEE", "EMP-223", "Palak Sharma", ankeshManager, true, "+91 98123 45223", "1999-05-16", "Female", "Sector 12, Panchkula");
            Employee nuri = createEmployee("Nuri", "Naz", "nuri@company.com", "Sales", "BDE", "EMPLOYEE", "EMP-224", "Nuri Naz", ankeshManager, true, "+91 98123 45224", "1998-11-20", "Female", "Sector 32, Chandigarh");
            Employee mehakD = createEmployee("Mehak", "Dhingra", "mehak.dhingra@company.com", "Design", "Graphic Designer", "EMPLOYEE", "EMP-225", "Mehak Dhingra", ankeshManager, true, "+91 98123 45225", "1997-09-08", "Female", "Phase 6, Mohali");
            Employee pradeep = createEmployee("Pradeep", "Negi", "pradeep@company.com", "Sales", "Sales Executive", "EMPLOYEE", "EMP-226", "Pradeep Negi", ankeshManager, true, "+91 98123 45226", "1996-02-24", "Male", "Zirakpur, Punjab");
            Employee sakshi = createEmployee("Sakshi", "Sharma", "sakshi@company.com", "Marketing", "Content Writer", "EMPLOYEE", "EMP-227", "Sakshi Sharma", ankeshManager, true, "+91 98123 45227", "1998-10-02", "Female", "Sector 8, Panchkula");
            Employee sahil = createEmployee("Sahil", "Billowria", "sahil@company.com", "Sales", "BDE", "EMPLOYEE", "EMP-228", "Sahil Billowria", ankeshManager, true, "+91 98123 45228", "1997-07-13", "Male", "Sector 40, Chandigarh");
            Employee tanzeel = createEmployee("Tanzeel", "Khan", "tanzeel@company.com", "Sales", "BDE", "EMPLOYEE", "EMP-231", "Tanzeel", ankeshManager, true, "+91 98123 45231", "1998-12-10", "Female", "Sector 22, Chandigarh");
            Employee tammana = createEmployee("Tammana", "Sharma", "tammana@company.com", "Design", "UI Designer", "EMPLOYEE", "EMP-232", "Tammana", ankeshManager, true, "+91 98123 45232", "1999-04-18", "Female", "Sector 35, Chandigarh");

            // Create User Login Credentials for all
            List<Employee> seededEmployees = List.of(ishu, lovish, abhishekG, abhinav, gurkirat, ashish, aniket, himanshu, kuldeep, abhishekT, parav, sandeep, anshu, nisha, mehak, harleen, jyoti, sadham, vishali, anjali, chanda, palak, nuri, mehakD, pradeep, sakshi, sahil, tanzeel, tammana);
            for (Employee emp : seededEmployees) {
                createUser(emp.getEmail(), "user123", "ROLE_EMPLOYEE", emp.getId());

                if (leaveBalanceRepository.findByEmployeeIdAndYear(emp.getId(), 2026).isEmpty()) {
                    LeaveBalance balance = LeaveBalance.builder()
                            .employeeId(emp.getId())
                            .year(2026)
                            .casualLeaveQuota(6.0)
                            .casualLeaveUsed(1.0)
                            .sickLeaveQuota(6.0)
                            .sickLeaveUsed(2.0)
                            .earnedLeaveQuota(6.0)
                            .earnedLeaveUsed(0.0)
                            .workFromHomeQuota(0.0)
                            .workFromHomeUsed(0.0)
                            .restrictedHolidayQuota(2.0)
                            .restrictedHolidayUsed(0.0)
                            .build();
                    leaveBalanceRepository.save(balance);
                }
            }

            // Seed 6-month historical payroll for demo employee (Lovish)
            payrollService.getPayrollHistory(lovish.getId());

            // Induce September 2026 Attendance, 2 Leaves, 2 Short Breaks, 3 Late, 1 Early Out for Lovish
            seedLovishSeptemberAttendanceAndLeaves(lovish);

            // Also seed realistic September 2026 demo attendance for Super Admin so test logins reflect live data
            employeeRepository.findByEmail("admin@company.com").ifPresent(admin -> {
                admin.setIsAttendanceTracked(true);
                employeeRepository.save(admin);
                seedLovishSeptemberAttendanceAndLeaves(admin);
            });

            log.info("Database successfully seeded with Aadisha Dhullar (HR), 3 Managers, all 29 Excel employees, Admin demo records, and 6-month Payroll history!");
    }

    private Employee createEmployee(String firstName, String lastName, String email, String dept, String designation, String role, String code, String biometricName, Employee manager, boolean isAttendanceTracked, String phone, String dob, String gender, String address) {
        Employee existing = employeeRepository.findByEmployeeCode(code)
                .orElseGet(() -> employeeRepository.findByEmail(email).orElse(null));

        LocalDate dateOfBirth = LocalDate.parse(dob);

        if (existing != null) {
            existing.setFirstName(firstName);
            existing.setLastName(lastName);
            existing.setEmail(email);
            existing.setDepartment(dept);
            existing.setDesignation(designation);
            existing.setRole(role);
            if (existing.getSystemRole() == null || "NONE".equals(existing.getSystemRole())) {
                existing.setSystemRole("SUPER_ADMIN".equalsIgnoreCase(role) ? "SUPER_ADMIN" : "NONE");
            }
            existing.setBiometricName(biometricName != null ? biometricName : (firstName + " " + lastName));
            existing.setManager(manager);
            existing.setIsAttendanceTracked(isAttendanceTracked);
            existing.setPhoneNumber(phone);
            existing.setDateOfBirth(dateOfBirth);
            existing.setGender(gender);
            existing.setAddress(address);
            existing.setEmergencyContactName("Family Contact (" + lastName + ")");
            existing.setEmergencyContactPhone("+91 98000 " + phone.substring(phone.length() - 5));
            return employeeRepository.save(existing);
        }

        Employee emp = Employee.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .department(dept)
                .designation(designation)
                .role(role)
                .systemRole("SUPER_ADMIN".equalsIgnoreCase(role) ? "SUPER_ADMIN" : "NONE")
                .employeeCode(code)
                .biometricName(biometricName != null ? biometricName : (firstName + " " + lastName))
                .joiningDate(LocalDate.of(2024, 1, 15))
                .employmentType("FULL_TIME")
                .status("ACTIVE")
                .phoneNumber(phone)
                .dateOfBirth(dateOfBirth)
                .gender(gender)
                .address(address)
                .emergencyContactName("Family Contact (" + lastName + ")")
                .emergencyContactPhone("+91 98000 " + phone.substring(phone.length() - 5))
                .isAttendanceTracked(isAttendanceTracked)
                .manager(manager)
                .build();
        return employeeRepository.save(emp);
    }

    private void createUser(String email, String password, String role, Long employeeId) {
        createUser(email, password, role, "NONE", employeeId);
    }

    private void createUser(String email, String password, String role, String systemRole, Long employeeId) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setSystemRole(systemRole != null ? systemRole : "NONE");
        user.setEmployeeId(employeeId);
        userRepository.save(user);
    }

    private void seedLovishSeptemberAttendanceAndLeaves(Employee lovish) {
        Long empId = lovish.getId();
        log.info("Seeding realistic September 2026 attendance and leave data for Lovish Kumar (empId={})...", empId);

        // 1. Ensure 2026 Leave Balances reflect 2 leaves taken (Casual & Sick)
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYear(empId, 2026)
                .orElseGet(() -> LeaveBalance.builder().employeeId(empId).year(2026).build());
        balance.setCasualLeaveQuota(6.0);
        balance.setCasualLeaveUsed(1.0); // 5 remaining
        balance.setSickLeaveQuota(6.0);
        balance.setSickLeaveUsed(1.0);   // 5 remaining
        balance.setEarnedLeaveQuota(6.0);
        balance.setEarnedLeaveUsed(0.0); // 6 remaining
        balance.setWorkFromHomeQuota(0.0);
        balance.setWorkFromHomeUsed(0.0);
        balance.setRestrictedHolidayQuota(2.0);
        balance.setRestrictedHolidayUsed(0.0);
        leaveBalanceRepository.save(balance);

        // 2. Ensure Leave Requests for September 2026 (2 Full Leaves, 2 Short Breaks)
        List<LeaveRequest> existingLeaves = leaveRequestRepository.findApprovedLeavesForEmployeeInRange(
                empId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        leaveRequestRepository.deleteAll(existingLeaves);

        // Leave 1: Casual Leave on Sep 4, 2026
        LeaveRequest casualLeave = LeaveRequest.builder()
                .employeeId(empId)
                .startDate(LocalDate.of(2026, 9, 4))
                .endDate(LocalDate.of(2026, 9, 4))
                .leaveType("CASUAL")
                .totalDays(1.0)
                .isTimeBased(false)
                .status("APPROVED")
                .reason("Family event")
                .build();

        // Leave 2: Sick Leave on Sep 18, 2026
        LeaveRequest sickLeave = LeaveRequest.builder()
                .employeeId(empId)
                .startDate(LocalDate.of(2026, 9, 18))
                .endDate(LocalDate.of(2026, 9, 18))
                .leaveType("SICK")
                .totalDays(1.0)
                .isTimeBased(false)
                .status("APPROVED")
                .reason("Seasonal fever and doctor consultation")
                .build();

        // Short Break 1: Sep 9, 2026 (11:30 AM - 12:15 PM)
        LeaveRequest shortBreak1 = LeaveRequest.builder()
                .employeeId(empId)
                .startDate(LocalDate.of(2026, 9, 9))
                .endDate(LocalDate.of(2026, 9, 9))
                .leaveType("SHORT_BREAK")
                .totalDays(0.0)
                .isTimeBased(true)
                .startTime(LocalTime.of(11, 30))
                .endTime(LocalTime.of(12, 15))
                .status("APPROVED")
                .reason("Urgent banking errand")
                .build();

        // Short Break 2: Sep 22, 2026 (04:15 PM - 05:00 PM)
        LeaveRequest shortBreak2 = LeaveRequest.builder()
                .employeeId(empId)
                .startDate(LocalDate.of(2026, 9, 22))
                .endDate(LocalDate.of(2026, 9, 22))
                .leaveType("SHORT_BREAK")
                .totalDays(0.0)
                .isTimeBased(true)
                .startTime(LocalTime.of(16, 15))
                .endTime(LocalTime.of(17, 0))
                .status("APPROVED")
                .reason("Medical prescription pickup")
                .build();

        leaveRequestRepository.saveAll(List.of(casualLeave, sickLeave, shortBreak1, shortBreak2));

        // 3. Clean and Seed Attendance Records for all working days up to Sep 28, 2026
        List<Attendance> existingAtt = attendanceRepository.findByEmployeeIdAndDateBetween(
                empId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        attendanceRepository.deleteAll(existingAtt);

        Map<LocalDate, AttendanceRecordSpec> schedule = new LinkedHashMap<>();
        schedule.put(LocalDate.of(2026, 9, 1), new AttendanceRecordSpec(LocalTime.of(9, 20), LocalTime.of(18, 30), "PRESENT"));
        schedule.put(LocalDate.of(2026, 9, 2), new AttendanceRecordSpec(LocalTime.of(9, 22), LocalTime.of(18, 35), "PRESENT"));
        schedule.put(LocalDate.of(2026, 9, 3), new AttendanceRecordSpec(LocalTime.of(9, 18), LocalTime.of(18, 30), "PRESENT"));
        // Sep 4 is Leave 1 (Full Day Casual Leave)
        schedule.put(LocalDate.of(2026, 9, 7), new AttendanceRecordSpec(LocalTime.of(9, 25), LocalTime.of(18, 30), "PRESENT"));
        schedule.put(LocalDate.of(2026, 9, 8), new AttendanceRecordSpec(LocalTime.of(9, 20), LocalTime.of(18, 32), "PRESENT"));
        schedule.put(LocalDate.of(2026, 9, 9), new AttendanceRecordSpec(LocalTime.of(9, 21), LocalTime.of(18, 30), "PRESENT")); // + Short Break 1
        schedule.put(LocalDate.of(2026, 9, 10), new AttendanceRecordSpec(LocalTime.of(9, 24), LocalTime.of(18, 30), "PRESENT"));
        // Late Arrival 1: Sep 11 (10:00 AM - 15 mins after grace limit)
        schedule.put(LocalDate.of(2026, 9, 11), new AttendanceRecordSpec(LocalTime.of(10, 0), LocalTime.of(18, 30), "LATE"));
        schedule.put(LocalDate.of(2026, 9, 14), new AttendanceRecordSpec(LocalTime.of(9, 23), LocalTime.of(18, 30), "PRESENT"));
        schedule.put(LocalDate.of(2026, 9, 15), new AttendanceRecordSpec(LocalTime.of(9, 20), LocalTime.of(18, 30), "PRESENT"));
        // Late Arrival 2: Sep 16 (10:00 AM - 15 mins after grace limit)
        schedule.put(LocalDate.of(2026, 9, 16), new AttendanceRecordSpec(LocalTime.of(10, 0), LocalTime.of(18, 30), "LATE"));
        schedule.put(LocalDate.of(2026, 9, 17), new AttendanceRecordSpec(LocalTime.of(9, 25), LocalTime.of(18, 30), "PRESENT"));
        // Sep 18 is Leave 2 (Full Day Sick Leave)
        schedule.put(LocalDate.of(2026, 9, 21), new AttendanceRecordSpec(LocalTime.of(9, 22), LocalTime.of(18, 30), "PRESENT"));
        schedule.put(LocalDate.of(2026, 9, 22), new AttendanceRecordSpec(LocalTime.of(9, 20), LocalTime.of(18, 30), "PRESENT")); // + Short Break 2
        schedule.put(LocalDate.of(2026, 9, 23), new AttendanceRecordSpec(LocalTime.of(9, 24), LocalTime.of(18, 30), "PRESENT"));
        // Early Out 1: Sep 24 (6:10 PM - 20 mins early before 6:30 PM)
        schedule.put(LocalDate.of(2026, 9, 24), new AttendanceRecordSpec(LocalTime.of(9, 20), LocalTime.of(18, 10), "PRESENT"));
        // Late Arrival 3: Sep 25 (10:00 AM - 15 mins after grace limit)
        schedule.put(LocalDate.of(2026, 9, 25), new AttendanceRecordSpec(LocalTime.of(10, 0), LocalTime.of(18, 30), "LATE"));
        // Today: Sep 28 (Present)
        schedule.put(LocalDate.of(2026, 9, 28), new AttendanceRecordSpec(LocalTime.of(9, 20), LocalTime.of(18, 30), "PRESENT"));

        for (Map.Entry<LocalDate, AttendanceRecordSpec> entry : schedule.entrySet()) {
            LocalDate d = entry.getKey();
            AttendanceRecordSpec spec = entry.getValue();

            Attendance att = attendanceRepository.findByEmployeeIdAndDate(empId, d)
                    .orElseGet(() -> {
                        Attendance a = new Attendance();
                        a.setEmployeeId(empId);
                        a.setDate(d);
                        return a;
                    });
            att.setCheckInTime(spec.in);
            att.setCheckOutTime(spec.out);
            att.setStatus(spec.status);
            att.setTotalWorkingMinutes((int) java.time.Duration.between(spec.in, spec.out).toMinutes());
            attendanceRepository.save(att);
        }

        log.info("Lovish Kumar September 2026 attendance seeded: 18 present, 3 late arrivals (15m past grace), 1 early departure (20m early), 2 full leaves, 2 short breaks.");
    }

    private static class AttendanceRecordSpec {
        LocalTime in;
        LocalTime out;
        String status;
        AttendanceRecordSpec(LocalTime in, LocalTime out, String status) {
            this.in = in;
            this.out = out;
            this.status = status;
        }
    }
}
