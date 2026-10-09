package com.example.hr_management_backend.features.email.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class PostmarkClient {

    @Value("${app.postmark.server-token:}")
    private String serverToken;

    @Value("${app.postmark.sender-email:lovishsharma@grootsoftwares.com}")
    private String senderEmail;

    @Value("${app.postmark.message-stream:outbound}")
    private String messageStream;

    @Value("${app.postmark.enabled:true}")
    private boolean enabled;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PostmarkClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.postmarkapp.com")
                .build();
    }

    public PostmarkSendResult sendEmail(String recipient, String subject, String htmlBody) {
        if (!enabled || serverToken == null || serverToken.isBlank()) {
            log.info("[Postmark Mock/Disabled] Mocking send to: {}, Subject: {}", recipient, subject);
            return new PostmarkSendResult(true, "MOCK-MSG-ID", "Postmark sending disabled or token not configured");
        }

        try {
            Map<String, Object> payload = Map.of(
                    "From", senderEmail,
                    "To", recipient,
                    "Subject", subject,
                    "HtmlBody", htmlBody,
                    "MessageStream", messageStream != null && !messageStream.isBlank() ? messageStream : "outbound"
            );

            log.info("[Postmark] Dispatching email to recipient: {}, Subject: {}", recipient, subject);

            String responseBody = restClient.post()
                    .uri("/email")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Postmark-Server-Token", serverToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            Map<?, ?> json = objectMapper.readValue(responseBody, Map.class);
            String messageId = json.get("MessageID") != null ? json.get("MessageID").toString() : null;
            log.info("[Postmark] Email successfully sent! MessageID: {}", messageId);
            return new PostmarkSendResult(true, messageId, null);
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException ex) {
            String errorMsg = ex.getResponseBodyAsString();
            log.warn("[Postmark API Error] Status: {}, Response: {}", ex.getStatusCode(), errorMsg);
            return new PostmarkSendResult(false, null, "Postmark HTTP " + ex.getStatusCode() + ": " + errorMsg);
        } catch (Exception e) {
            log.error("[Postmark Error] Unexpected exception sending email to {}: {}", recipient, e.getMessage(), e);
            return new PostmarkSendResult(false, null, e.getMessage());
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PostmarkSendResult {
        private boolean success;
        private String messageId;
        private String errorMessage;
    }
}
