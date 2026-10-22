package com.example.hr_management_backend.features.notifications.service;

import com.example.hr_management_backend.features.notifications.dto.RegisterDeviceTokenRequest;
import com.example.hr_management_backend.features.notifications.model.DeviceToken;
import com.example.hr_management_backend.features.notifications.repository.DeviceTokenRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    @Transactional
    public void registerDeviceToken(Long employeeId, RegisterDeviceTokenRequest request) {
        String token = request.getToken().trim();
        Optional<DeviceToken> existingOpt = deviceTokenRepository.findByToken(token);

        if (existingOpt.isPresent()) {
            DeviceToken existing = existingOpt.get();
            existing.setEmployeeId(employeeId);
            existing.setDeviceType(request.getDeviceType());
            existing.setDeviceName(request.getDeviceName());
            existing.setLastSeenAt(LocalDateTime.now());
            deviceTokenRepository.save(existing);
            log.info("[PushNotificationService] Updated existing device token for employeeId={}", employeeId);
        } else {
            DeviceToken newToken = DeviceToken.builder()
                    .employeeId(employeeId)
                    .token(token)
                    .deviceType(request.getDeviceType())
                    .deviceName(request.getDeviceName())
                    .lastSeenAt(LocalDateTime.now())
                    .build();
            deviceTokenRepository.save(newToken);
            log.info("[PushNotificationService] Registered new device token for employeeId={}", employeeId);
        }
    }

    @Transactional
    public void unregisterDeviceToken(String token) {
        if (token != null && !token.isBlank()) {
            deviceTokenRepository.deleteByToken(token.trim());
            log.info("[PushNotificationService] Unregistered device token");
        }
    }

    /**
     * Send direct push notification to an individual employee's registered devices.
     */
    public void sendToEmployee(Long employeeId, String title, String body, Map<String, String> data) {
        List<String> tokens = deviceTokenRepository.findTokensByEmployeeId(employeeId);
        if (tokens.isEmpty()) {
            log.debug("[PushNotificationService] No active device tokens found for employeeId={}", employeeId);
            return;
        }
        dispatchMulticast(tokens, title, body, data);
    }

    /**
     * Broadcast notification to all employees in the organization.
     */
    public void broadcastToAll(String title, String body, Map<String, String> data) {
        List<String> tokens = deviceTokenRepository.findAllActiveTokens();
        if (tokens.isEmpty()) {
            log.debug("[PushNotificationService] No active device tokens found for broadcast");
            return;
        }
        dispatchMulticast(tokens, title, body, data);
    }

    /**
     * Broadcast notification to specific target employees (e.g. departmental broadcast).
     */
    public void sendToEmployees(Collection<Long> employeeIds, String title, String body, Map<String, String> data) {
        if (employeeIds == null || employeeIds.isEmpty()) return;
        List<String> tokens = deviceTokenRepository.findTokensByEmployeeIds(employeeIds);
        if (tokens.isEmpty()) return;
        dispatchMulticast(tokens, title, body, data);
    }

    private void dispatchMulticast(List<String> tokens, String title, String body, Map<String, String> data) {
        if (firebaseMessaging == null) {
            log.warn("[PushNotificationService] FirebaseMessaging not configured. Skipping dispatch: title='{}'", title);
            return;
        }

        // FCM Multicast limit is 500 tokens per batch
        final int batchSize = 500;
        List<String> staleTokens = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i += batchSize) {
            List<String> batch = tokens.subList(i, Math.min(i + batchSize, tokens.size()));

            MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                    .addAllTokens(batch)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            try {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(messageBuilder.build());
                log.info("[PushNotificationService] FCM Batch sent: {} successes, {} failures out of {} tokens",
                        response.getSuccessCount(), response.getFailureCount(), batch.size());

                if (response.getFailureCount() > 0) {
                    List<SendResponse> responses = response.getResponses();
                    for (int j = 0; j < responses.size(); j++) {
                        if (!responses.get(j).isSuccessful()) {
                            MessagingErrorCode errorCode = responses.get(j).getException().getMessagingErrorCode();
                            if (errorCode == MessagingErrorCode.UNREGISTERED ||
                                errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                                staleTokens.add(batch.get(j));
                            }
                        }
                    }
                }
            } catch (FirebaseMessagingException e) {
                log.error("[PushNotificationService] Failed to dispatch FCM multicast batch: {}", e.getMessage(), e);
            }
        }

        // Clean up stale tokens asynchronously to maintain DB cleanliness & query performance
        if (!staleTokens.isEmpty()) {
            purgeStaleTokens(staleTokens);
        }
    }

    @Transactional
    public void purgeStaleTokens(List<String> staleTokens) {
        try {
            int deleted = deviceTokenRepository.deleteByTokenIn(staleTokens);
            log.info("[PushNotificationService] Purged {} unregistered/stale FCM device token(s) from database", deleted);
        } catch (Exception e) {
            log.error("[PushNotificationService] Error pruning stale tokens: {}", e.getMessage());
        }
    }
}
