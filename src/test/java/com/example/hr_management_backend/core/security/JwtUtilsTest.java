package com.example.hr_management_backend.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000);
    }

    @Test
    void generateAndValidateJwtToken() {
        String email = "testuser@company.com";
        String token = jwtUtils.generateTokenFromEmail(email);

        assertNotNull(token);
        assertTrue(jwtUtils.validateJwtToken(token));
        assertEquals(email, jwtUtils.getUserNameFromJwtToken(token));
    }

    @Test
    void validateInvalidJwtToken() {
        assertFalse(jwtUtils.validateJwtToken("invalid.jwt.token"));
    }
}
