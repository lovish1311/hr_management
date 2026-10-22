package com.example.hr_management_backend.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                java.util.List<String> authStrings = jwtUtils.getAuthoritiesFromJwtToken(jwt);

                java.util.List<org.springframework.security.core.GrantedAuthority> authorities;
                UserPrincipal principal;

                if (authStrings != null && !authStrings.isEmpty()) {
                    // ZERO-QUERY FAST PATH: Direct in-memory authentication from cryptographically verified claims
                    Long userId = jwtUtils.getClaimLong(jwt, "userId");
                    Long employeeId = jwtUtils.getClaimLong(jwt, "employeeId");
                    String role = jwtUtils.getClaimString(jwt, "role");
                    String systemRole = jwtUtils.getClaimString(jwt, "systemRole");

                    java.util.Set<String> resolvedAuths = new java.util.LinkedHashSet<>();
                    for (String a : authStrings) {
                        if (a != null && a.startsWith("ROLE_")) {
                            resolvedAuths.add(a.trim());
                        }
                    }

                    if (employeeId != null) {
                        try {
                            java.util.List<String> dynamicAuths = userDetailsService.getDynamicAuthorities(employeeId);
                            if (dynamicAuths != null) {
                                for (String da : dynamicAuths) {
                                    if (da != null && !da.isBlank()) {
                                        resolvedAuths.add(da.trim().toUpperCase());
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    } else {
                        resolvedAuths.addAll(authStrings);
                    }

                    authorities = resolvedAuths.stream()
                            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                            .collect(java.util.stream.Collectors.toList());

                    principal = UserPrincipal.builder()
                            .id(userId)
                            .email(username)
                            .password("")
                            .employeeId(employeeId)
                            .role(role != null ? role : "EMPLOYEE")
                            .systemRole(systemRole != null ? systemRole : "NONE")
                            .authorities(authorities)
                            .accountStatus("ACTIVE")
                            .isActivated(true)
                            .build();
                } else {
                    // Fallback to DB for legacy tokens
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    authorities = new java.util.ArrayList<>(userDetails.getAuthorities());
                    if (userDetails instanceof UserPrincipal up) {
                        principal = up;
                    } else {
                        principal = UserPrincipal.builder()
                                .email(username)
                                .password("")
                                .authorities(authorities)
                                .accountStatus("ACTIVE")
                                .isActivated(true)
                                .build();
                    }
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                authorities
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
