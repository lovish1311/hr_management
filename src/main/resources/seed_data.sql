-- Sample Data Script for HR Management Database

-- 1. Employees
INSERT INTO employees (first_name, last_name, email, department, role) VALUES
('John', 'Doe', 'john.doe@company.com', 'Engineering', 'Senior Software Engineer'),
('Jane', 'Smith', 'jane.smith@company.com', 'Human Resources', 'HR Manager'),
('Alice', 'Johnson', 'alice.johnson@company.com', 'Engineering', 'Frontend Developer'),
('Bob', 'Williams', 'bob.williams@company.com', 'Finance', 'Financial Analyst'),
('Charlie', 'Brown', 'charlie.brown@company.com', 'Marketing', 'Marketing Specialist');

-- 2. Attendance Records
INSERT INTO attendance (employee_id, date, check_in_time, check_out_time, status) VALUES
(1, '2026-08-01', '09:00:00', '17:30:00', 'PRESENT'),
(1, '2026-08-02', '09:15:00', '17:45:00', 'LATE'),
(2, '2026-08-01', '08:55:00', '17:00:00', 'PRESENT'),
(3, '2026-08-01', '09:05:00', '17:30:00', 'PRESENT'),
(4, '2026-08-01', '00:00:00', '00:00:00', 'ABSENT');

-- 3. Leave Requests
INSERT INTO leave_requests (employee_id, start_date, end_date, reason, status) VALUES
(1, '2026-08-10', '2026-08-14', 'Annual Vacation', 'APPROVED'),
(3, '2026-08-05', '2026-08-06', 'Personal Medical Leave', 'PENDING'),
(4, '2026-08-01', '2026-08-01', 'Sick Leave', 'APPROVED');

-- 4. Payroll Records
INSERT INTO payroll (employee_id, base_salary, bonuses, deductions, net_salary, pay_period) VALUES
(1, 85000.0, 5000.0, 12000.0, 78000.0, '2026-07'),
(2, 75000.0, 3000.0, 10000.0, 68000.0, '2026-07'),
(3, 70000.0, 2500.0, 9500.0, 63000.0, '2026-07'),
(4, 65000.0, 2000.0, 8500.0, 58500.0, '2026-07'),
(5, 60000.0, 1500.0, 8000.0, 53500.0, '2026-07');

-- 5. Job Postings
INSERT INTO job_postings (title, description, department, status) VALUES
('Full Stack Java Developer', 'Looking for an experienced Spring Boot + React developer.', 'Engineering', 'OPEN'),
('HR Specialist', 'Responsible for employee onboarding and talent acquisition.', 'Human Resources', 'OPEN'),
('Senior Accountant', 'Managing corporate financial reporting and audits.', 'Finance', 'CLOSED');

-- 6. Performance Reviews
INSERT INTO performance_reviews (employee_id, review_period, score, feedback) VALUES
(1, 'Q2-2026', 5, 'Exceeded expectations in lead technical delivery.'),
(2, 'Q2-2026', 4, 'Great work on streamlining the onboarding workflow.'),
(3, 'Q2-2026', 4, 'Delivered user interface improvements on schedule.');

-- 7. Settings
INSERT INTO settings (key_name, key_value) VALUES
('company_name', 'HR Management Inc.'),
('working_hours_per_day', '8'),
('currency', 'USD')
ON CONFLICT (key_name) DO NOTHING;
