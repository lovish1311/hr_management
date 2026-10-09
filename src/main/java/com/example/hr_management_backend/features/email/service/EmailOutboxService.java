package com.example.hr_management_backend.features.email.service;

import com.example.hr_management_backend.features.email.model.EmailOutbox;
import com.example.hr_management_backend.features.email.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOutboxService {

    private final EmailOutboxRepository outboxRepository;
    private final OutboxDispatcherService dispatcherService;

    @Transactional
    public EmailOutbox queueEmail(String recipient, String subject, String htmlBody) {
        EmailOutbox outbox = EmailOutbox.builder()
                .recipient(recipient)
                .subject(subject)
                .htmlBody(htmlBody)
                .status("PENDING")
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        EmailOutbox saved = outboxRepository.save(outbox);
        log.info("[Outbox] Queued email #{} for recipient: {}, Subject: {}", saved.getId(), recipient, subject);

        // Trigger non-blocking async dispatch only AFTER database transaction commits
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                dispatcherService.dispatchPendingAsync();
                            } catch (Exception e) {
                                log.warn("[Outbox] Async dispatch trigger deferred to scheduler: {}", e.getMessage());
                            }
                        }
                    }
            );
        } else {
            try {
                dispatcherService.dispatchPendingAsync();
            } catch (Exception e) {
                log.warn("[Outbox] Async dispatch trigger deferred to scheduler: {}", e.getMessage());
            }
        }

        return saved;
    }

    public void sendPasswordResetOtp(String recipient, String recipientName, String otpCode) {
        String name = recipientName != null && !recipientName.isBlank() ? recipientName : "Employee";
        String subject = "Your Password Reset Code: " + otpCode + " - HR Management";
        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin: 0; padding: 0; background-color: #F8FAFC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                  <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #F8FAFC; padding: 40px 20px;">
                    <tr>
                      <td align="center">
                        <table width="560" border="0" cellspacing="0" cellpadding="0" style="background-color: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.06); border: 1px solid #E2E8F0;">
                          <tr>
                            <td style="background: linear-gradient(135deg, #0F172A 0%%, #0D9488 100%%); padding: 32px 40px; text-align: left;">
                              <h1 style="margin: 0; color: #FFFFFF; font-size: 24px; font-weight: 700; letter-spacing: -0.5px;">HR Enterprise Portal</h1>
                              <p style="margin: 6px 0 0 0; color: #99F6E4; font-size: 13px;">Security & Account Management</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding: 40px;">
                              <h2 style="margin: 0 0 16px 0; color: #0F172A; font-size: 20px; font-weight: 700;">Password Reset Request</h2>
                              <p style="margin: 0 0 24px 0; color: #475569; font-size: 15px; line-height: 1.6;">
                                Hello <strong>%s</strong>,<br><br>
                                We received a request to reset the password for your account. Please use the verification code below to authorize your password change:
                              </p>
                              
                              <div style="background-color: #F0FDFA; border: 2px dashed #0D9488; border-radius: 12px; padding: 24px; text-align: center; margin-bottom: 28px;">
                                <span style="font-size: 36px; font-weight: 800; color: #0F172A; letter-spacing: 8px; font-family: 'Courier New', monospace;">%s</span>
                                <div style="margin-top: 8px; color: #0D9488; font-size: 12px; font-weight: 600;">Valid for 15 minutes</div>
                              </div>
                              
                              <p style="margin: 0 0 16px 0; color: #64748B; font-size: 13px; line-height: 1.5;">
                                If you did not request this code, you can safely ignore this email or contact your HR administrator if you suspect unauthorized activity.
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="background-color: #F8FAFC; padding: 20px 40px; border-top: 1px solid #E2E8F0; text-align: center;">
                              <p style="margin: 0; color: #94A3B8; font-size: 12px;">This is an automated notification from HR Management Enterprise System.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(name, otpCode);

        queueEmail(recipient, subject, html);
    }

    public void sendEmployeeActivationKey(String recipient, String recipientName, String activationKey, String tempLoginRole) {
        String name = recipientName != null && !recipientName.isBlank() ? recipientName : "New Team Member";
        String subject = "Welcome to the Team! Set Up Your HR Account";
        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin: 0; padding: 0; background-color: #F8FAFC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                  <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #F8FAFC; padding: 40px 20px;">
                    <tr>
                      <td align="center">
                        <table width="560" border="0" cellspacing="0" cellpadding="0" style="background-color: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.06); border: 1px solid #E2E8F0;">
                          <tr>
                            <td style="background: linear-gradient(135deg, #0F172A 0%%, #0D9488 100%%); padding: 32px 40px; text-align: left;">
                              <h1 style="margin: 0; color: #FFFFFF; font-size: 24px; font-weight: 700;">Welcome to the Team!</h1>
                              <p style="margin: 6px 0 0 0; color: #99F6E4; font-size: 13px;">Employee Onboarding Portal</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding: 40px;">
                              <h2 style="margin: 0 0 16px 0; color: #0F172A; font-size: 20px; font-weight: 700;">Your Account Has Been Created</h2>
                              <p style="margin: 0 0 20px 0; color: #475569; font-size: 15px; line-height: 1.6;">
                                Hello <strong>%s</strong>,<br><br>
                                An employee account has been created for you with role <strong>%s</strong>.
                                To log in for the first time, use your one-time activation key below to configure your permanent password:
                              </p>
                              
                              <div style="background-color: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 12px; padding: 20px; margin-bottom: 24px;">
                                <div style="color: #64748B; font-size: 12px; font-weight: 600; text-transform: uppercase;">Your Registered Email:</div>
                                <div style="color: #0F172A; font-size: 15px; font-weight: 700; margin-bottom: 12px;">%s</div>
                                
                                <div style="color: #64748B; font-size: 12px; font-weight: 600; text-transform: uppercase;">One-Time Activation Key:</div>
                                <div style="font-size: 24px; font-weight: 800; color: #0D9488; font-family: 'Courier New', monospace; letter-spacing: 2px;">%s</div>
                              </div>
                              
                              <p style="margin: 0 0 8px 0; color: #334155; font-size: 14px; line-height: 1.5;">
                                <strong>How to activate:</strong>
                              </p>
                              <ol style="margin: 0 0 24px 0; padding-left: 20px; color: #475569; font-size: 14px; line-height: 1.6;">
                                <li>Open the HR Management Mobile/Web App.</li>
                                <li>Switch to <strong>Production Mode</strong> on the Login screen.</li>
                                <li>Click <strong>Activate Account</strong>, enter your key, and choose your secure password.</li>
                              </ol>
                            </td>
                          </tr>
                          <tr>
                            <td style="background-color: #F8FAFC; padding: 20px 40px; border-top: 1px solid #E2E8F0; text-align: center;">
                              <p style="margin: 0; color: #94A3B8; font-size: 12px;">HR Management Enterprise • Confidential</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(name, tempLoginRole != null ? tempLoginRole : "EMPLOYEE", recipient, activationKey);

        queueEmail(recipient, subject, html);
    }

    public void sendLeaveAppliedNotification(String managerEmail, String managerName, String employeeName, String leaveType, String startDate, String endDate, String reason) {
        String subject = "New Leave Application: " + employeeName + " (" + leaveType + ")";
        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin: 0; padding: 0; background-color: #F8FAFC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
                  <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #F8FAFC; padding: 30px 15px;">
                    <tr>
                      <td align="center">
                        <table width="560" border="0" cellspacing="0" cellpadding="0" style="background-color: #FFFFFF; border-radius: 14px; border: 1px solid #E2E8F0; padding: 32px;">
                          <tr>
                            <td>
                              <h2 style="color: #0F172A; margin: 0 0 16px 0;">New Leave Request Awaiting Review</h2>
                              <p style="color: #475569; font-size: 15px; margin: 0 0 20px 0;">
                                Hello <strong>%s</strong>,<br><br>
                                <strong>%s</strong> has submitted a leave request that requires your review:
                              </p>
                              <div style="background-color: #F1F5F9; border-radius: 8px; padding: 16px; margin-bottom: 24px; font-size: 14px; color: #1E293B;">
                                <p style="margin: 4px 0;"><strong>Leave Type:</strong> %s</p>
                                <p style="margin: 4px 0;"><strong>Duration:</strong> %s to %s</p>
                                <p style="margin: 4px 0;"><strong>Reason:</strong> %s</p>
                              </div>
                              <p style="color: #64748B; font-size: 13px;">Please log in to the HR Portal to approve or reject this request.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(managerName != null ? managerName : "Manager", employeeName, leaveType, startDate, endDate, reason != null ? reason : "None provided");

        queueEmail(managerEmail, subject, html);
    }

    public void sendLeaveDecisionNotification(String employeeEmail, String employeeName, String leaveType, String startDate, String endDate, String status, String managerComments) {
        boolean isApproved = "APPROVED".equalsIgnoreCase(status);
        String subject = "Leave Request " + (isApproved ? "Approved" : "Rejected") + ": " + startDate + " to " + endDate;
        String badgeColor = isApproved ? "#10B981" : "#EF4444";
        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin: 0; padding: 0; background-color: #F8FAFC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
                  <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #F8FAFC; padding: 30px 15px;">
                    <tr>
                      <td align="center">
                        <table width="560" border="0" cellspacing="0" cellpadding="0" style="background-color: #FFFFFF; border-radius: 14px; border: 1px solid #E2E8F0; padding: 32px;">
                          <tr>
                            <td>
                              <span style="display: inline-block; background-color: %s; color: white; padding: 4px 12px; border-radius: 12px; font-weight: bold; font-size: 12px; margin-bottom: 12px;">%s</span>
                              <h2 style="color: #0F172A; margin: 0 0 16px 0;">Your Leave Request has been %s</h2>
                              <p style="color: #475569; font-size: 15px; margin: 0 0 20px 0;">
                                Hello <strong>%s</strong>,<br><br>
                                Your leave application for <strong>%s</strong> from <strong>%s</strong> to <strong>%s</strong> has been <strong>%s</strong>.
                              </p>
                              %s
                              <p style="color: #64748B; font-size: 13px; margin-top: 20px;">You can review your updated leave balance in your profile.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(badgeColor, status, status, employeeName, leaveType, startDate, endDate, status,
                managerComments != null && !managerComments.isBlank() ? "<p style='color: #475569;'><strong>Notes:</strong> " + managerComments + "</p>" : "");

        queueEmail(employeeEmail, subject, html);
    }
}
