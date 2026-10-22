package com.example.hr_management_backend.features.notifications.controller;

import com.example.hr_management_backend.core.security.UserPrincipal;
import com.example.hr_management_backend.features.notifications.dto.RegisterDeviceTokenRequest;
import com.example.hr_management_backend.features.notifications.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final PushNotificationService pushNotificationService;

    @PostMapping("/register")
    public ResponseEntity<?> registerToken(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody RegisterDeviceTokenRequest request
    ) {
        if (userPrincipal == null || userPrincipal.getEmployeeId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Authenticated employee session required"));
        }

        pushNotificationService.registerDeviceToken(userPrincipal.getEmployeeId(), request);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Device token registered successfully"));
    }

    @DeleteMapping("/unregister")
    public ResponseEntity<?> unregisterToken(
            @RequestParam("token") String token
    ) {
        pushNotificationService.unregisterDeviceToken(token);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Device token removed"));
    }
}
