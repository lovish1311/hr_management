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
| **4. Attendance Management** | ✅ Implemented (Check-in/out, History, Late/Absent Tracking) | ⏳ In Progress / Pending UI | ⏳ **Backend Ready** |
| **5. Leave Management** | ✅ Implemented (Apply, Approve/Reject, Balance, History) | ⏳ Partial (`leave_request_tile` component created) | ⏳ **Backend Ready / UI In Progress** |
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

### 4. Attendance Management (Backend Complete)
- **Backend**:
  - Endpoints for marking daily attendance, check-in, and check-out (`/api/v1/attendance`) ([AttendanceController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/attendance/controller/AttendanceController.java)).
  - Attendance history, filtering by date ranges and employee ID.
  - Automatic late and absent tracking logic.

### 5. Leave Management (Backend Complete & Partial UI)
- **Backend**:
  - Submit leave requests with start/end dates, leave types, and reasons (`/api/v1/leaves`) ([LeaveController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/leaves/controller/LeaveController.java)).
  - Manager/Admin approval and rejection workflows (`/api/v1/leaves/{id}/status`).
  - Leave balance and request history tracking.
- **Frontend**:
  - Core leave request preview widget (`leave_request_tile.dart`).

### 6. Payroll, Performance, Recruitment & Settings (Backend Complete)
- **Backend**:
  - **Payroll**: Salary records, monthly summary reports, payslip status tracking (`/api/v1/payroll`) ([PayrollController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/payroll/controller/PayrollController.java)).
  - **Recruitment**: Job posting CRUD, applicant tracking (`/api/v1/recruitment`) ([RecruitmentController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/recruitment/controller/RecruitmentController.java)).
  - **Performance**: Performance reviews, goal setting, ratings, manager feedback (`/api/v1/performance`) ([PerformanceController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/performance/controller/PerformanceController.java)).
  - **Settings**: System-wide configuration key-value storage (`/api/v1/settings`) ([SettingsController.java](file:///c:/Users/Lovish/Projects/hr_management/src/main/java/com/example/hr_management_backend/features/settings/controller/SettingsController.java)).

---

## ⏳ Features Yet to be Implemented / Roadmap

### 1. Frontend UI Screens (Immediate Next Steps)
- [ ] **Attendance Management UI**:
  - Check-in / Check-out button widget & timer.
  - Calendar/List view for monthly attendance history.
- [ ] **Leave Management UI**:
  - Interactive "Apply for Leave" modal & form.
  - Admin/Manager Leave Approval dashboard.
  - Leave balance visualization cards.
- [ ] **Payroll Management UI**:
  - Payslip view & PDF download trigger.
  - Monthly payroll summary charts.
- [ ] **Recruitment Portal UI**:
  - Job openings directory & applicant submission view.
- [ ] **Performance Management UI**:
  - Performance review scorecards and feedback forms.
- [ ] **Settings & User Profile UI**:
  - Edit profile page & password change modal.
  - Dark mode / Light mode theme toggle setting.

### 2. Backend & System Enhancements
- [ ] **Push & In-App Notifications Module**:
  - Real-time alert trigger when leave is approved/rejected.
  - Attendance reminder cron/notifications.
- [ ] **Database Migration Scripts**:
  - Flyway / Liquibase SQL migration scripts for schema versioning in production.
- [ ] **Redis Caching**:
  - Cache low-frequency read APIs (e.g. Dashboard stats, Settings).

---

## 👥 Main Users & Role Permissions
- **HR Admin**: Full access to employee records, payroll, recruitment, company settings, and system reports.
- **Manager**: Access to team attendance, team leave approvals, performance evaluations, and department stats.
- **Employee**: Access to personal profile, attendance check-in, personal leave requests, and payslips.

---

## 🎨 UI & Architectural Principles
- **Backend Guidelines**: Modular Controller-Service-Repository flow, DTO separation, REST RFC 7807 problem details, constructor injection.
- **Frontend Architecture**: Feature-first layered structure (`data`, `domain`, `presentation`), clean routing, responsive layout, dark/light theme support.

