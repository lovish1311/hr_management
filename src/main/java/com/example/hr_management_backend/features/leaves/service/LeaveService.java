package com.example.hr_management_backend.features.leaves.service;

import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;

    @Autowired
    public LeaveService(LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public LeaveRequest applyForLeave(LeaveRequest request) {
        request.setId(null);
        request.setStatus("PENDING");
        return leaveRequestRepository.save(request);
    }

    public LeaveRequest updateStatus(Long requestId, String status) {
        LeaveRequest request = leaveRequestRepository.findById(requestId).orElse(null);
        if (request != null) {
            request.setStatus(status);
            return leaveRequestRepository.save(request);
        }
        return null;
    }

    public List<LeaveRequest> getLeavesByEmployee(Long employeeId) {
        return leaveRequestRepository.findByEmployeeId(employeeId);
    }

    public List<LeaveRequest> getAllLeaveRequests() {
        return leaveRequestRepository.findAll();
    }
}
