package com.example.hr_management_backend.features.email.service;

import com.example.hr_management_backend.features.email.client.PostmarkClient;
import com.example.hr_management_backend.features.email.model.EmailOutbox;
import com.example.hr_management_backend.features.email.repository.EmailOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class OutboxDispatcherService {

    private final EmailOutboxRepository outboxRepository;
    private final PostmarkClient postmarkClient;
    private final TransactionTemplate transactionTemplate;

    public OutboxDispatcherService(EmailOutboxRepository outboxRepository,
                                  PostmarkClient postmarkClient,
                                  PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.postmarkClient = postmarkClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Async("dbExecutor")
    public void dispatchPendingAsync() {
        processPendingBatch();
    }

    @Scheduled(fixedDelay = 15000)
    public void processScheduledBatch() {
        processPendingBatch();
    }

    /**
     * Atomically claims a batch of pending emails using PostgreSQL FOR UPDATE SKIP LOCKED.
     * Transitions claimed rows from PENDING to PROCESSING in a short-lived isolated transaction,
     * guaranteeing zero duplicate dispatches across multiple horizontally-scaled instances.
     */
    public List<EmailOutbox> claimAndLockPendingBatch(LocalDateTime now, int batchSize) {
        return transactionTemplate.execute(status -> {
            LocalDateTime staleThreshold = now.minusMinutes(5);
            List<Long> claimedIds = outboxRepository.claimPendingIds(now, staleThreshold, batchSize);
            if (claimedIds == null || claimedIds.isEmpty()) {
                return List.of();
            }
            outboxRepository.markAsProcessing(claimedIds, now);
            return outboxRepository.findAllByIdIn(claimedIds);
        });
    }

    /**
     * Non-blocking batch coordinator: Claims items in an isolated transaction,
     * then executes external HTTP Postmark I/O without holding any database locks.
     */
    public void processPendingBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<EmailOutbox> claimedList;
        try {
            claimedList = claimAndLockPendingBatch(now, 25);
        } catch (Exception ex) {
            log.error("[Outbox Dispatcher] Error claiming pending batch: {}", ex.getMessage(), ex);
            return;
        }

        if (claimedList == null || claimedList.isEmpty()) {
            return;
        }

        log.info("[Outbox Dispatcher] Claimed batch of {} outbox email(s) via SKIP LOCKED", claimedList.size());
        for (EmailOutbox item : claimedList) {
            processSingleOutboxItem(item, now);
        }
    }

    private void processSingleOutboxItem(EmailOutbox item, LocalDateTime now) {
        try {
            // External I/O executed OUTSIDE database transaction
            PostmarkClient.PostmarkSendResult result = postmarkClient.sendEmail(
                    item.getRecipient(),
                    item.getSubject(),
                    item.getHtmlBody()
            );

            // Record outcome in an isolated transaction
            recordDeliveryOutcome(item.getId(), result, now);
        } catch (Exception ex) {
            log.error("[Outbox Dispatcher] Unexpected error dispatching outbox #{}: {}", item.getId(), ex.getMessage(), ex);
            recordExceptionOutcome(item.getId(), ex.getMessage(), now);
        }
    }

    public void recordDeliveryOutcome(Long outboxId, PostmarkClient.PostmarkSendResult result, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            EmailOutbox item = outboxRepository.findById(outboxId).orElse(null);
            if (item == null) return;

            if (result.isSuccess()) {
                item.setStatus("SENT");
                item.setPostmarkMessageId(result.getMessageId());
                item.setErrorMessage(null);
                outboxRepository.save(item);
                log.info("[Outbox Dispatcher] Email ID #{} marked SENT (Postmark Msg ID: {})", item.getId(), result.getMessageId());
            } else {
                int retries = item.getRetryCount() != null ? item.getRetryCount() + 1 : 1;
                item.setRetryCount(retries);
                item.setErrorMessage(result.getErrorMessage());

                if (retries >= 5) {
                    item.setStatus("FAILED");
                    log.warn("[Outbox Dispatcher] Email ID #{} reached max retries (5). Marked FAILED. Error: {}", item.getId(), result.getErrorMessage());
                } else {
                    // Return to PENDING with exponential backoff: 20s, 80s, 320s, 1280s
                    item.setStatus("PENDING");
                    long backoffSeconds = (long) Math.pow(4, retries) * 10;
                    item.setNextRetryAt(now.plusSeconds(backoffSeconds));
                    log.info("[Outbox Dispatcher] Email ID #{} scheduled for retry #{} at {}", item.getId(), retries, item.getNextRetryAt());
                }
                outboxRepository.save(item);
            }
        });
    }

    public void recordExceptionOutcome(Long outboxId, String errorMsg, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            EmailOutbox item = outboxRepository.findById(outboxId).orElse(null);
            if (item == null) return;

            int retries = item.getRetryCount() != null ? item.getRetryCount() + 1 : 1;
            item.setRetryCount(retries);
            item.setErrorMessage(errorMsg);
            if (retries >= 5) {
                item.setStatus("FAILED");
            } else {
                item.setStatus("PENDING");
                long backoffSeconds = (long) Math.pow(4, retries) * 10;
                item.setNextRetryAt(now.plusSeconds(backoffSeconds));
            }
            outboxRepository.save(item);
        });
    }
}
