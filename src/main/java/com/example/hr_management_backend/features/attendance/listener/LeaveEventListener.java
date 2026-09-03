package com.example.hr_management_backend.features.attendance.listener;

import com.example.hr_management_backend.features.attendance.service.AttendanceService;
import com.example.hr_management_backend.features.leaves.event.LeaveApprovedEvent;
import com.example.hr_management_backend.features.leaves.event.LeaveWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaveEventListener {

    private final AttendanceService attendanceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLeaveApproved(LeaveApprovedEvent event) {
        log.info("Handling LeaveApprovedEvent for employeeId={}: {} to {}",
                event.getEmployeeId(), event.getStartDate(), event.getEndDate());
        try {
            attendanceService.syncLeaveToAttendance(
                    event.getEmployeeId(),
                    event.getStartDate(),
                    event.getEndDate(),
                    event.getLeaveType()
            );
        } catch (Exception e) {
            log.error("Failed to sync leave to attendance for employeeId={}: {}", event.getEmployeeId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLeaveWithdrawn(LeaveWithdrawnEvent event) {
        log.info("Handling LeaveWithdrawnEvent for employeeId={}: {} to {}",
                event.getEmployeeId(), event.getStartDate(), event.getEndDate());
        try {
            attendanceService.removeLeaveFromAttendance(
                    event.getEmployeeId(),
                    event.getStartDate(),
                    event.getEndDate()
            );
        } catch (Exception e) {
            log.error("Failed to remove leave from attendance for employeeId={}: {}", event.getEmployeeId(), e.getMessage(), e);
        }
    }
}
