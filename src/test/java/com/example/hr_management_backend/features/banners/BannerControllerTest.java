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
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bannerController).build();
        bannerRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsAdmin() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
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

        ResponseEntity<List<BannerDto>> response = bannerController.getActiveBanners(principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
