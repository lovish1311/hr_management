# Backend Development Guidelines & Best Practices

This document outlines the architecture, coding standards, performance optimization strategies, and production-readiness guidelines for the **HR Management Backend**.

---

## 1. Architecture & Design Patterns

### Controller-Service-Repository Separation
Keep a strict unidirectional data flow: `Controller` ➔ `Service` ➔ `Repository`.
- **Controllers**: Only handle request mapping, path/query validation, routing, and returning responses. They must not contain business logic.
- **Services**: Contain all domain business logic, calculations, transaction boundaries, and orchestration.
- **Repositories**: Handle database interactions. Do not leak repository concepts into the controller layer.

### Dependency Injection Standard
Explicitly ban field injection (`@Autowired` on variables) in favor of **Constructor Injection**. This ensures immutability and makes unit testing easier without spinning up the Spring Context.
- **Field Injection (Avoid)**:
  ```java
  @Autowired
  private EmployeeRepository repo; // Tightly couples class to Spring, allows mutable state, makes testing harder
  ```
- **Constructor Injection (Best)**:
  ```java
  private final EmployeeRepository repo; // Enforces immutability (final keyword), fails fast on startup if missing
  // Optionally use Lombok's @RequiredArgsConstructor to generate the constructor automatically.
  ```

### Data Transfer Objects (DTOs)
- **Do not expose database entities directly** in controller endpoints. Exposing entities leaks the database schema and makes API versioning difficult.
- Use **DTOs** (Data Transfer Objects) for request payloads and response bodies.
- Map DTOs to/from Entities in the Service layer (using tools like MapStruct or manual builders).

### Standardized API Routing & Versioning
- Require that all endpoints are versioned and pluralized (e.g., `/api/v1/employees` instead of `/api/employee`).
- This prevents breaking client applications (e.g. mobile Flutter app) if you ever need to change the API contract in the future.

### Input Validation
- Validate all incoming DTO requests using `jakarta.validation` annotations (e.g., `@NotNull`, `@Email`, `@Size`, `@Min`).
- Annotate controller request parameters with `@Valid` to automatically trigger validation.
- Let [GlobalExceptionHandler](file:///c:/Users/Lovish/flutter-projects/backend_hr/hr_management/src/main/java/com/example/hr_management_backend/core/exception/GlobalExceptionHandler.java) capture validation errors and return Spring Boot 3's **`ProblemDetail` (RFC 7807)** consistent formatted responses.

---

## 2. Coding Standards & Clean Code

### Reduce Boilerplate with Lombok
- Add **Project Lombok** to compile-time dependencies to eliminate boilerplate code like getters, setters, constructors, and builders (`@Data`, `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`).

### SOLID & DRY Principles
- **Single Responsibility (SRP)**: Each class should have one, and only one, reason to change.
- **Don't Repeat Yourself (DRY)**: Abstract common utilities (date calculations, parsing, auth context parsing) into helper classes/services under `core.utils`.

---

## 3. Database & Persistence Best Practices

### Transaction Management (`@Transactional`)
- All service methods that modify data (Create, Update, Delete) must be annotated with `@Transactional`.
- This ensures data integrity: if an error occurs halfway through a complex operation, the database rolls back completely, preventing partial data corruption.

### Production Migrations (Flyway / Liquibase)
- While `spring.jpa.hibernate.ddl-auto=update` is convenient for local development, it is **extremely dangerous** in production.
- Use a database migration tool like **Flyway** or **Liquibase** to version control database schema changes incrementally using SQL scripts.

### Indexes & Performance
- Add database indexes on columns frequently used in search filters and `WHERE` clauses (e.g., `employee_id` in Attendance/Payroll, `email` in Employee/Auth).
- Define indexes inside entity annotations:
  ```java
  @Table(name = "attendance", indexes = @Index(name = "idx_attendance_employee", columnList = "employeeId"))
  ```

### Paginated Queries
- Avoid returning massive collections from database queries. Use Spring Data's `Pageable` and `Slice` to return paginated lists:
  ```java
  Page<Employee> findAll(Pageable pageable);
  ```

### N+1 Query Problem Prevention
- Use `@EntityGraph` or custom JPQL joins (`JOIN FETCH`) when retrieving entities with relationships to avoid executing multiple nested queries.

---

## 4. Environment Profiles

- A robust architecture requires completely isolated configurations for different environments.
- Use **Spring Profiles**:
  - `application-dev.properties`: Configured with `ddl-auto=update` and local PostgreSQL.
  - `application-prod.properties`: Configured with strict Flyway migrations and a managed cloud database.

---

## 5. High Performance & Scaling

### Connection Pool Optimization (HikariCP)
- Fine-tune connection pool properties in [application.properties](file:///c:/Users/Lovish/flutter-projects/backend_hr/hr_management/src/main/resources/application.properties):
  ```properties
  spring.datasource.hikari.maximum-pool-size=20
  spring.datasource.hikari.minimum-idle=5
  spring.datasource.hikari.idle-timeout=300000
  spring.datasource.hikari.connection-timeout=20000
  ```

### Caching
- Implement Spring Cache abstraction (`@Cacheable`) with a caching provider like Redis or Caffeine for read-heavy, low-frequency write data (such as settings, holiday lists, or static lists).

### Asynchronous Operations (`@Async`)
- Offload non-blocking operations like sending emails, sending push notifications, or processing reports to background thread pools using Spring's `@Async` annotation.
- **Warning**: Methods annotated with `@Async` must be called from a *different* class to work. Due to how Spring proxies work, calling an async method from within the same class runs it synchronously.

---

## 6. Testing Strategy

A production-readiness document is incomplete without defining how the code is verified:
- **Unit Tests**: Mandate unit tests for the Service layer (using Mockito to mock the repositories).
- **Integration Tests**: Mandate integration tests for the Controllers to verify HTTP status codes, routing, and serialization.

---

## 7. Security & Production Readiness

### Spring Security & JWT Authentication
- Secure all REST endpoints with role-based access control (RBAC).
- Implement JWT (JSON Web Token) authentication to handle stateless API calls from the Flutter client.

### Monitoring & Actuator
- Add the `spring-boot-starter-actuator` dependency to expose endpoints for health checks, metrics, and application info (secure these in production so they aren't publicly accessible).

### Structured Logging
- Use SLF4J with Logback for structured logging.
- Avoid printing logs using `System.out.println()`. Use appropriate log levels:
  - `ERROR`: System-wide failure that requires attention.
  - `WARN`: Unexpected event, but system recovered.
  - `INFO`: Significant lifecycle occurrences (startup, shutdown, cron execution).
  - `DEBUG`: Internal variables, flow trace (disabled in production).
