# HR Management App - Features and Specifications

## Project Overview
This app is an HR management system for handling employee-related work in one place. The main goal is to make HR tasks simple, fast, organized, highly scalable, and production-ready.

---

## 📊 Feature Implementation Status Matrix

| Feature | Backend Status | Frontend Status | Overall Status |
| :--- | :--- | :--- | :--- |
| **1. Authentication** | ✅ Implemented (JWT, RBAC, Password Hashing) | ✅ Implemented (Login Screen, Token Storage, Router Guards) | ✅ **Completed** |
| **2. Dashboard** | ✅ Implemented (Stats API, Employee & Department Metrics) | ✅ Implemented (Dashboard Screen, KPI Cards, Events Widget) | ✅ **Completed** |
| **3. Employee Management** | ✅ Implemented (CRUD APIs, Search, Department Filter) | ✅ Implemented (Directory View, Profile Details Page) | ✅ **Completed** |
| **4. Attendance & Biometric Beautifier** | ✅ Implemented (Auto-Beautify Excel Import via Python, Check-in/out, History) | ✅ Implemented (Attendance Calendar & Biometric Import UI) | ✅ **Completed** |
| **5. Leave Management & Policy Engine** | ✅ Implemented (Full/Half Day, Short Break/Early Out, Deductions, Auto Sync) | ✅ Implemented (Apply Form, HR/Admin Approval Dashboard, Calendar Sync) | ✅ **Completed** |
| **6. Payroll Management** | ✅ Implemented (Salary Records, Monthly Summary, Payslips) | ❌ Pending | ⏳ **Backend Ready** |
| **7. Recruitment** | ✅ Implemented (Job Postings, Candidate Applications) | ❌ Pending | ⏳ **Backend Ready** |
| **8. Performance Management** | ✅ Implemented (Goals, Reviews, Ratings & Feedback) | ❌ Pending | ⏳ **Backend Ready** |
| **9. Settings & System Prefs** | ✅ Implemented (Company Settings, App Config Key-Values) | ❌ Pending | ⏳ **Backend Ready** |
| **10. Notifications** | ❌ Pending | ❌ Pending | ❌ **Yet to be Implemented** |

---

## ✅ Implemented Features (Detailed Breakdown)

### 1. Authentication & Security
- **Backend**:
  - Stateless JWT token generation and verification ([JwtUtils.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/core/security/JwtUtils.java), [JwtAuthenticationFilter.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/core/security/JwtAuthenticationFilter.java)).
  - Secure password storage using `BCryptPasswordEncoder`.
  - Role-based Access Control (RBAC) supporting `ROLE_ADMIN`, `ROLE_EMPLOYEE`, and `ROLE_MANAGER`.
  - `/api/v1/auth/login` and `/api/v1/auth/register` REST endpoints ([AuthController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/auth/controller/AuthController.java)).
- **Frontend**:
  - Clean Flutter login interface (`login_page.dart`).
  - Secure local token persistence (`auth_storage.dart` via `SharedPreferences`).
  - Dynamic navigation routing based on authentication state (`router.dart`).

### 2. Dashboard Analytics
- **Backend**:
  - `/api/v1/dashboard/stats` endpoint delivering real-time metrics ([DashboardController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/dashboard/controller/DashboardController.java)):
    - Total Employee count
    - Active Employees
    - Employees on Leave
    - Pending Approvals count
    - Total Departments
- **Frontend**:
  - Responsive Dashboard screen (`dashboard_page.dart`).
  - Modern KPI statistic cards (`kpi_card.dart`).
  - Leave summary overview and upcoming company events widget.
  - Side navigation drawer (`hr_drawer.dart`).

### 3. Employee Management
- **Backend**:
  - Full CRUD operations via `/api/v1/employees` ([EmployeeController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/employees/controller/EmployeeController.java)).
  - Search, filtering by department/role, and pagination support.
  - Domain models mapped to clean DTOs ([Employee.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/employees/model/Employee.java), [EmployeeRepository.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/employees/repository/EmployeeRepository.java)).
- **Frontend**:
  - Employee directory grid/list view (`employee_directory_page.dart`).
  - Detailed employee profile viewer (`employee_profile_page.dart`).
  - Interactive employee card components (`employee_card.dart`).
  - API repository implementation with offline/dummy fallback for resilient testing.

### 4. Attendance Management & Automated Biometric Import (Fully Functional)
- **Automated Excel Beautifier Integration**:
  - Automatically executes Python script (`attendance_beautifier.py`) upon uploading raw biometric Excel files at `/api/v1/attendance/import-biometric`.
  - Re-formats columns, cleans employee names, standardizes `In 1`, `Out 1`, `In 2`, `Out 2` timestamps into AM/PM, and calculates exact working hours.
- **Backend & UI**:
  - Endpoints for marking daily attendance, check-in, check-out, and biometric excel import ([AttendanceController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/attendance/controller/AttendanceController.java)).
  - Attendance history, calendar month view, filtering by employee ID and date ranges.

### 5. Leave Management & Policy Engine (Fully Functional)
- **Leave Policy Engine**:
  - Full-day leaves (Casual, Sick, Earned, WFH), Half-day leaves (Session 1, Session 2), Short Break & Early Out permission requests.
  - Deduction Modes supported: `UNIT_SEPARATE` (quota limit with 0.5-day CL penalty for 3rd+ unit), `UNIT_COMBINED`, `HOURLY_SEPARATE`, and `HOURLY_COMBINED`.
  - Double-booking protection (same-session overlap validation) and rejection protection (balance unchanged if rejected).
  - Automatic sync with daily Attendance records upon HR/Admin approval.
- **Frontend & Verification**:
  - Multi-role Leave Application, Management, and Approval UI pages in Flutter.
  - End-to-End Playwright visual & balance verification suite (`test_full_leave_suite.py` and `test_time_off_deductions.py`).

### 6. Payroll, Performance, Recruitment & Settings (Backend Complete)
- **Backend**:
  - **Payroll**: Salary records, monthly summary reports, payslip status tracking (`/api/v1/payroll`) ([PayrollController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/payroll/controller/PayrollController.java)).
  - **Recruitment**: Job posting CRUD, applicant tracking (`/api/v1/recruitment`) ([RecruitmentController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/recruitment/controller/RecruitmentController.java)).
  - **Performance**: Performance reviews, goal setting, ratings, manager feedback (`/api/v1/performance`) ([PerformanceController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/performance/controller/PerformanceController.java)).
  - **Settings**: System-wide configuration key-value storage (`/api/v1/settings`) ([SettingsController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/settings/controller/SettingsController.java)).

---

## ⏳ Pending Items & Advanced Roadmap

### 1. Leave System Advanced Roadmap
- [ ] **Medical Certificate Attachment Upload**: Support uploading doctor's note / proof for Sick Leaves exceeding 2 days.
- [ ] **Weekend & Public Holiday Exclusion Engine**: Auto-exclude non-working days/holidays from total leave duration calculations (e.g. Fri-Mon = 2 leave days).
- [ ] **Yearly Carry-Forward & Encashment**: Scheduled Jan 1st cron job for Earned Leave rollover and Casual/Sick leave resets.
- [ ] **Monthly Accrual Engine**: Incremental credit of 1.5 Earned Leaves per completed month instead of annual upfront allocation.
- [ ] **Work Handover / Cover Person Selection**: Selecting a colleague to cover duties during leave.

### 2. UI Screens & System Enhancements
- [ ] **Payroll UI**: Payslip preview widget, salary breakdown modal, and PDF download trigger.
- [ ] **Recruitment Portal UI**: Candidate application tracking dashboard and job vacancy manager.
- [ ] **Performance UI**: Quarterly review scorecards and performance goal setup forms.
- [ ] **Notifications Module**: In-app notifications & email alerts for leave approvals and attendance warnings.
- [ ] **Redis Caching & Concurrency Locking**: Redis caching for read-heavy APIs and DB pessimistic locking during balance deductions.

## 👥 Main Users & Role Permissions
- **HR Admin**: Full access to employee records, payroll, recruitment, company settings, and system reports.
- **Manager**: Access to team attendance, team leave approvals, performance evaluations, and department stats.
- **Employee**: Access to personal profile, attendance check-in, personal leave requests, and payslips.

---

## 🎨 UI & Architectural Principles
- **Backend Guidelines**: Modular Controller-Service-Repository flow, DTO separation, REST RFC 7807 problem details, constructor injection.
- **Frontend Architecture**: Feature-first layered structure (`data`, `domain`, `presentation`), clean routing, responsive layout, dark/light theme support.

