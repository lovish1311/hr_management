package com.example.hr_management_backend.features.banners;

import com.example.hr_management_backend.core.security.UserPrincipal;
import com.example.hr_management_backend.features.banners.controller.BannerController;
import com.example.hr_management_backend.features.banners.dto.BannerDto;
import com.example.hr_management_backend.features.banners.dto.CreateBannerRequest;
import com.example.hr_management_backend.features.banners.dto.UpdateBannerRequest;
import com.example.hr_management_backend.features.banners.model.BannerCategory;
import com.example.hr_management_backend.features.banners.repository.BannerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BannerControllerTest {

    @Autowired
    private BannerController bannerController;

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private com.example.hr_management_backend.features.banners.repository.BannerReactionRepository bannerReactionRepository;

    @Autowired
    private com.example.hr_management_backend.features.employees.repository.EmployeeRepository employeeRepository;

    @Autowired
    private com.example.hr_management_backend.features.employees.repository.EmployeeAuthorityRepository employeeAuthorityRepository;

    @Autowired
    private com.example.hr_management_backend.core.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private com.example.hr_management_backend.core.security.JwtUtils jwtUtils;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bannerController)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new com.example.hr_management_backend.core.exception.GlobalExceptionHandler())
                .build();
        bannerReactionRepository.deleteAll();
        bannerRepository.deleteAll();
        if (cacheManager.getCache("user_authorities") != null) {
            cacheManager.getCache("user_authorities").clear();
        }
        if (cacheManager.getCache("banner_candidates") != null) {
            cacheManager.getCache("banner_candidates").clear();
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsAdmin() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .employeeId(1L)
                .email("admin@company.com")
                .role("ADMIN")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("BANNER_MANAGE")))
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateAsEmployee() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(2L)
                .employeeId(2L)
                .email("employee@company.com")
                .role("EMPLOYEE")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Active banners endpoint returns 200 OK with list of banners")
    void testGetActiveBanners_ReturnsOk() throws Exception {
        authenticateAsEmployee();
        LocalDateTime now = LocalDateTime.now();

        bannerRepository.save(com.example.hr_management_backend.features.banners.model.Banner.builder()
                .title("Company Hackathon 2026")
                .subtitle("Build innovative tools")
                .category(BannerCategory.OFFICE_EVENT)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .displayOrder(0)
                .isActive(true)
                .version(0L)
                .build());

        mockMvc.perform(get("/api/v1/banners/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Company Hackathon 2026")));
    }

    @Test
    @DisplayName("Manage endpoint returns all banners when authenticated as admin")
    void testGetAllBannersForManagement() throws Exception {
        authenticateAsAdmin();
        mockMvc.perform(get("/api/v1/banners/manage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Create banner endpoint creates banner with 201 Created when authenticated as admin")
    void testCreateBanner_ReturnsCreated() throws Exception {
        authenticateAsAdmin();
        LocalDateTime now = LocalDateTime.now();
        CreateBannerRequest req = CreateBannerRequest.builder()
                .title("Festival Celebration")
                .subtitle("Special festivities planned")
                .category(BannerCategory.FESTIVAL)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .displayOrder(1)
                .isActive(true)
                .build();

        mockMvc.perform(post("/api/v1/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", is("Festival Celebration")));
    }

    @Test
    @DisplayName("Update, toggle, and delete banner endpoints when authenticated as admin")
    void testUpdateToggleDeleteBanner() throws Exception {
        authenticateAsAdmin();
        LocalDateTime now = LocalDateTime.now();
        com.example.hr_management_backend.features.banners.model.Banner saved = bannerRepository.save(
                com.example.hr_management_backend.features.banners.model.Banner.builder()
                        .title("Original Banner")
                        .category(BannerCategory.ANNOUNCEMENT)
                        .startDate(now.minusHours(1))
                        .expiryDate(now.plusDays(2))
                        .displayOrder(0)
                        .isActive(true)
                        .version(0L)
                        .build()
        );

        UpdateBannerRequest updateReq = UpdateBannerRequest.builder()
                .title("Updated Title")
                .category(BannerCategory.ANNOUNCEMENT)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .displayOrder(0)
                .isActive(true)
                .version(0L)
                .build();

        mockMvc.perform(put("/api/v1/banners/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")));

        // Toggle status
        mockMvc.perform(patch("/api/v1/banners/" + saved.getId() + "/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(false)));

        // Delete
        mockMvc.perform(delete("/api/v1/banners/" + saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Direct Controller Invocation with Principal resolves user department")
    void testControllerWithUserPrincipal() {
        authenticateAsAdmin();
        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .email("admin@company.com")
                .employeeId(1L)
                .build();

        ResponseEntity<List<BannerDto>> response = bannerController.getActiveBanners(null, principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Department filter IDOR prevention: regular employee cannot spoof other department banners")
    void testDepartmentFilter_IDOR_PreventionOnController() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Create banners: one for ENGINEERING, one for EXECUTIVE
        bannerRepository.save(com.example.hr_management_backend.features.banners.model.Banner.builder()
                .title("Engineering Tech Talk")
                .targetDepartment("ENGINEERING")
                .category(BannerCategory.OFFICE_EVENT)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .isActive(true)
                .version(0L)
                .build());

        bannerRepository.save(com.example.hr_management_backend.features.banners.model.Banner.builder()
                .title("Executive Compensation Review")
                .targetDepartment("EXECUTIVE")
                .category(BannerCategory.ANNOUNCEMENT)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .isActive(true)
                .version(0L)
                .build());

        // 2. Create Engineering employee
        com.example.hr_management_backend.features.employees.model.Employee dev =
                employeeRepository.save(com.example.hr_management_backend.features.employees.model.Employee.builder()
                        .firstName("Dev")
                        .lastName("Engineer")
                        .email("dev.engineer@company.com")
                        .department("ENGINEERING")
                        .role("EMPLOYEE")
                        .systemRole("NONE")
                        .build());

        UserPrincipal regularEmployeePrincipal = UserPrincipal.builder()
                .id(dev.getId())
                .employeeId(dev.getId())
                .email(dev.getEmail())
                .role("EMPLOYEE")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                .build();

        // Employee attempts IDOR attack by requesting ?department=EXECUTIVE
        ResponseEntity<List<BannerDto>> empResponse =
                bannerController.getActiveBanners("EXECUTIVE", regularEmployeePrincipal);

        assertEquals(HttpStatus.OK, empResponse.getStatusCode());
        assertNotNull(empResponse.getBody());
        assertEquals(1, empResponse.getBody().size());
        assertEquals("Engineering Tech Talk", empResponse.getBody().get(0).getTitle());
        // Verify Executive banner was NOT leaked!
        assertFalse(empResponse.getBody().stream().anyMatch(b -> "Executive Compensation Review".equals(b.getTitle())));

        // 3. Privileged Admin request CAN preview EXECUTIVE
        UserPrincipal adminPrincipal = UserPrincipal.builder()
                .id(999L)
                .email("admin@company.com")
                .role("ADMIN")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();

        ResponseEntity<List<BannerDto>> adminResponse =
                bannerController.getActiveBanners("EXECUTIVE", adminPrincipal);

        assertEquals(HttpStatus.OK, adminResponse.getStatusCode());
        assertNotNull(adminResponse.getBody());
        assertEquals(1, adminResponse.getBody().size());
        assertEquals("Executive Compensation Review", adminResponse.getBody().get(0).getTitle());
    }

    @Test
    @DisplayName("Revoking BANNER_MANAGE authority takes effect immediately in JwtAuthenticationFilter without waiting for JWT expiry")
    void testRevokeBannerManageAuthority_ImmediatelyBlocksAccess() throws Exception {
        // 1. Create employee in DB
        com.example.hr_management_backend.features.employees.model.Employee emp =
                employeeRepository.save(com.example.hr_management_backend.features.employees.model.Employee.builder()
                        .firstName("Banner")
                        .lastName("Delegate")
                        .email("delegate@company.com")
                        .department("MARKETING")
                        .role("EMPLOYEE")
                        .systemRole("NONE")
                        .build());

        // 2. Initially grant BANNER_MANAGE in DB
        com.example.hr_management_backend.features.employees.model.EmployeeAuthority auth =
                employeeAuthorityRepository.save(com.example.hr_management_backend.features.employees.model.EmployeeAuthority.builder()
                        .employeeId(emp.getId())
                        .authority("BANNER_MANAGE")
                        .grantedBy("admin@company.com")
                        .build());

        // 3. Issue a JWT containing BANNER_MANAGE baked into claims
        UserPrincipal principalWithPerm = UserPrincipal.builder()
                .id(emp.getId())
                .employeeId(emp.getId())
                .email(emp.getEmail())
                .role("EMPLOYEE")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("BANNER_MANAGE")))
                .build();

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principalWithPerm, null, principalWithPerm.getAuthorities());
        String jwtToken = jwtUtils.generateJwtToken(authToken);

        // Verify that with authority in DB, request authenticates with BANNER_MANAGE
        org.springframework.mock.web.MockHttpServletRequest req1 = new org.springframework.mock.web.MockHttpServletRequest();
        req1.addHeader("Authorization", "Bearer " + jwtToken);
        org.springframework.mock.web.MockHttpServletResponse res1 = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain chain1 = new org.springframework.mock.web.MockFilterChain();

        jwtAuthenticationFilter.doFilter(req1, res1, chain1);
        var auth1 = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth1);
        assertTrue(auth1.getAuthorities().stream().anyMatch(a -> "BANNER_MANAGE".equals(a.getAuthority())));

        // 4. NOW REVOKE BANNER_MANAGE FROM DB & EVICT CACHE
        employeeAuthorityRepository.delete(auth);
        if (cacheManager.getCache("user_authorities") != null) {
            cacheManager.getCache("user_authorities").evict(emp.getId());
        }
        SecurityContextHolder.clearContext();

        // 5. Incoming request with the SAME unexpired JWT token
        org.springframework.mock.web.MockHttpServletRequest req2 = new org.springframework.mock.web.MockHttpServletRequest();
        req2.addHeader("Authorization", "Bearer " + jwtToken);
        org.springframework.mock.web.MockHttpServletResponse res2 = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain chain2 = new org.springframework.mock.web.MockFilterChain();

        jwtAuthenticationFilter.doFilter(req2, res2, chain2);
        var auth2 = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth2);

        // Security check: BANNER_MANAGE must be stripped! Only ROLE_EMPLOYEE remains!
        assertTrue(auth2.getAuthorities().stream().anyMatch(a -> "ROLE_EMPLOYEE".equals(a.getAuthority())));
        assertFalse(auth2.getAuthorities().stream().anyMatch(a -> "BANNER_MANAGE".equals(a.getAuthority())),
                "Revoked BANNER_MANAGE authority must not persist from static JWT claims!");
    }

    @Test
    @DisplayName("POST /{id}/react toggles emoji reactions and returns authoritative reaction summary")
    void testToggleReaction_EndpointReturnsAuthoritativeSummary() throws Exception {
        authenticateAsEmployee();
        LocalDateTime now = LocalDateTime.now();

        var banner = bannerRepository.save(com.example.hr_management_backend.features.banners.model.Banner.builder()
                .title("Reaction Post")
                .category(BannerCategory.ANNOUNCEMENT)
                .bannerType(com.example.hr_management_backend.features.banners.model.BannerType.NEWS)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .isActive(true)
                .displayOrder(0)
                .build());

        // 1. Add LIKE
        mockMvc.perform(post("/api/v1/banners/" + banner.getId() + "/react")
                        .param("reactionType", "LIKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerId").value(banner.getId()))
                .andExpect(jsonPath("$.reactionCounts.LIKE").value(1))
                .andExpect(jsonPath("$.userReactions[0]").value("LIKE"))
                .andExpect(jsonPath("$.totalReactions").value(1));

        // 2. Toggle off LIKE
        mockMvc.perform(post("/api/v1/banners/" + banner.getId() + "/react")
                        .param("reactionType", "LIKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactionCounts.LIKE").value(0))
                .andExpect(jsonPath("$.totalReactions").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/banners/engage returns filtered news or celebration feeds")
    void testEngageFeed_EndpointReturnsFilteredResults() throws Exception {
        authenticateAsEmployee();
        LocalDateTime now = LocalDateTime.now();

        bannerRepository.save(com.example.hr_management_backend.features.banners.model.Banner.builder()
                .title("Company Hackathon 2026")
                .category(BannerCategory.OFFICE_EVENT)
                .bannerType(com.example.hr_management_backend.features.banners.model.BannerType.NEWS)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .isActive(true)
                .displayOrder(0)
                .build());

        mockMvc.perform(get("/api/v1/banners/engage")
                        .param("tab", "NEWS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Company Hackathon 2026"))
                .andExpect(jsonPath("$[0].bannerType").value("NEWS"));
    }

    @Test
    @DisplayName("POST /upload-image validates magic bytes and stores image; GET /images/{filename} serves image publicly")
    void testUploadAndServeBannerImage() throws Exception {
        authenticateAsAdmin();

        // Valid PNG header (89 50 4E 47 0D 0A 1A 0A) followed by padding
        byte[] validPngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0};
        org.springframework.mock.web.MockMultipartFile validFile =
                new org.springframework.mock.web.MockMultipartFile("file", "banner_test.png", "image/png", validPngBytes);

        String responseJson = mockMvc.perform(multipart("/api/v1/banners/upload-image").file(validFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").isNotEmpty())
                .andExpect(jsonPath("$.imageUrl").value(containsString("/api/v1/banners/images/")))
                .andReturn().getResponse().getContentAsString();

        String filename = objectMapper.readTree(responseJson).get("filename").asText();

        // Public retrieval test (works without authentication)
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/v1/banners/images/" + filename))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public, max-age=")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("inline; filename=")))
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("POST /upload-image rejects invalid binary files that fail magic byte verification")
    void testUploadBannerImage_RejectsInvalidFiles() throws Exception {
        authenticateAsAdmin();

        // Invalid fake image (text disguised as jpg)
        org.springframework.mock.web.MockMultipartFile invalidFile =
                new org.springframework.mock.web.MockMultipartFile("file", "exploit.jpg", "image/jpeg", "not a real image".getBytes());

        mockMvc.perform(multipart("/api/v1/banners/upload-image").file(invalidFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{id}/react supports explicit action (ADD / REMOVE) for network idempotency")
    void testToggleReaction_ExplicitActionParam() throws Exception {
        authenticateAsEmployee();

        LocalDateTime now = LocalDateTime.now();
        var banner = bannerRepository.save(com.example.hr_management_backend.features.banners.model.Banner.builder()
                .title("Reactions Post")
                .category(BannerCategory.ANNOUNCEMENT)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .isActive(true)
                .build());

        // ADD
        mockMvc.perform(post("/api/v1/banners/" + banner.getId() + "/react")
                        .param("reactionType", "LOVE")
                        .param("action", "ADD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactionCounts.LOVE").value(1))
                .andExpect(jsonPath("$.userReactions", hasItem("LOVE")));

        // ADD again (idempotent)
        mockMvc.perform(post("/api/v1/banners/" + banner.getId() + "/react")
                        .param("reactionType", "LOVE")
                        .param("action", "ADD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactionCounts.LOVE").value(1))
                .andExpect(jsonPath("$.userReactions", hasItem("LOVE")));

        // REMOVE
        mockMvc.perform(post("/api/v1/banners/" + banner.getId() + "/react")
                        .param("reactionType", "LOVE")
                        .param("action", "REMOVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactionCounts.LOVE").value(0))
                .andExpect(jsonPath("$.userReactions", not(hasItem("LOVE"))));
    }
}
