package com.example.hr_management_backend.features.attendance.service;

import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class AttendanceService {
    
    private final AttendanceRepository attendanceRepository;

    @Autowired
    public AttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public Attendance markAttendance(Long employeeId, String status) {
        Attendance attendance = new Attendance(
                null,
                employeeId,
                LocalDate.now(),
                LocalTime.now(),
                null,
                status
        );
        return attendanceRepository.save(attendance);
    }

    public List<Attendance> getAttendanceHistory(Long employeeId) {
        return attendanceRepository.findByEmployeeId(employeeId);
    }
}
