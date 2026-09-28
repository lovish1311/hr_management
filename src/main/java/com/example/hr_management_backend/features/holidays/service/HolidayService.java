package com.example.hr_management_backend.features.holidays.service;

import com.example.hr_management_backend.features.holidays.dto.EmployeeHolidayCalendarDto;
import com.example.hr_management_backend.features.holidays.dto.HolidayDto;
import com.example.hr_management_backend.features.holidays.dto.HolidayListDto;
import com.example.hr_management_backend.features.holidays.model.Holiday;
import com.example.hr_management_backend.features.holidays.model.HolidayList;
import com.example.hr_management_backend.features.holidays.repository.HolidayListRepository;
import com.example.hr_management_backend.features.holidays.repository.HolidayRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import com.example.hr_management_backend.features.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HolidayService {

    private final HolidayListRepository holidayListRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveService leaveService;
    private final SettingsService settingsService;

    // ==========================================
    // ADMIN: Holiday List Management
    // ==========================================

    @Transactional(readOnly = true)
    public List<HolidayListDto> getHolidayListsByYear(Integer year) {
        List<HolidayList> lists = holidayListRepository.findByYearOrderByCreatedAtDesc(year);
        return lists.stream().map(this::mapToListDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HolidayListDto getHolidayListDetails(Long id) {
        HolidayList list = holidayListRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday List not found with id: " + id));
        return mapToListDto(list);
    }

    @Transactional
    @CacheEvict(value = {"holidays_published", "attendance_calendar"}, allEntries = true)
    public HolidayList createHolidayList(HolidayList list, Long adminUserId) {
        if (list.getYear() == null) {
            throw new IllegalArgumentException("Year is required for a Holiday List");
        }
        if (list.getName() == null || list.getName().isBlank()) {
            throw new IllegalArgumentException("Holiday List name is required");
        }

        if (holidayListRepository.existsByYearAndName(list.getYear(), list.getName().trim())) {
            throw new IllegalArgumentException("A Holiday List with the name '" + list.getName() + "' already exists for year " + list.getYear());
        }

        list.setName(list.getName().trim());
        list.setCreatedBy(adminUserId);
        if (list.getActive() == null) list.setActive(true);
        if (list.getPublished() == null) list.setPublished(false);
        if (list.getApplicableGroup() == null || list.getApplicableGroup().isBlank()) list.setApplicableGroup("ALL");

        log.info("Creating new Holiday List '{}' for year {} by admin {}", list.getName(), list.getYear(), adminUserId);
        return holidayListRepository.save(list);
    }

    @Transactional
    @CacheEvict(value = {"holidays_published", "attendance_calendar"}, allEntries = true)
    public HolidayList updateHolidayList(Long id, HolidayList updated) {
        HolidayList existing = holidayListRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday List not found with id: " + id));

        if (updated.getName() != null && !updated.getName().isBlank()) {
            String newName = updated.getName().trim();
            if (holidayListRepository.existsByYearAndNameAndIdNot(existing.getYear(), newName, id)) {
                throw new IllegalArgumentException("Another Holiday List with name '" + newName + "' exists for year " + existing.getYear());
            }
            existing.setName(newName);
        }

        if (updated.getDescription() != null) {
            existing.setDescription(updated.getDescription());
        }
        if (updated.getActive() != null) {
            existing.setActive(updated.getActive());
        }
        if (updated.getApplicableGroup() != null) {
            existing.setApplicableGroup(updated.getApplicableGroup());
        }

        return holidayListRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = {"holidays_published", "attendance_calendar"}, allEntries = true)
    public HolidayList togglePublishHolidayList(Long id, boolean published) {
        HolidayList existing = holidayListRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday List not found with id: " + id));

        existing.setPublished(published);
        log.info("Holiday List '{}' (id: {}) publication set to: {}", existing.getName(), id, published);
        return holidayListRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = {"holidays_published", "attendance_calendar"}, allEntries = true)
    public void deleteHolidayList(Long id) {
        HolidayList existing = holidayListRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday List not found with id: " + id));

        List<Holiday> holidays = holidayRepository.findByHolidayListIdOrderByDateAsc(id);
        holidayRepository.deleteAll(holidays);
        holidayListRepository.delete(existing);
        log.info("Deleted Holiday List '{}' (id: {}) and {} associated holidays", existing.getName(), id, holidays.size());
    }

    // ==========================================
    // ADMIN: Holiday Entries Management
    // ==========================================

    @Transactional
    @CacheEvict(value = {"holidays_published", "attendance_calendar"}, allEntries = true)
    public Holiday addHoliday(Long holidayListId, Holiday holiday) {
        HolidayList list = holidayListRepository.findById(holidayListId)
                .orElseThrow(() -> new IllegalArgumentException("Holiday List not found with id: " + holidayListId));

        if (holiday.getName() == null || holiday.getName().isBlank()) {
            throw new IllegalArgumentException("Holiday name is required");
        }
        if (holiday.getDate() == null) {
            throw new IllegalArgumentException("Holiday date is required");
        }
        if (holiday.getType() == null || (!holiday.getType().equalsIgnoreCase("GENERAL") && !holiday.getType().equalsIgnoreCase("RESTRICTED"))) {
            throw new IllegalArgumentException("Holiday type must be either GENERAL or RESTRICTED");
        }

        // Year isolation rule: Date must belong to the list's year
        if (holiday.getDate().getYear() != list.getYear()) {
            throw new IllegalArgumentException("Holiday date (" + holiday.getDate() + ") does not match Holiday List year (" + list.getYear() + ")");
        }

        // Duplicate prevention rule
        if (holidayRepository.existsByHolidayListIdAndDate(holidayListId, holiday.getDate())) {
            throw new IllegalArgumentException("A holiday is already configured for date: " + holiday.getDate() + " in this Holiday List");
        }

        holiday.setId(null);
        holiday.setHolidayListId(holidayListId);
        holiday.setName(holiday.getName().trim());
        holiday.setType(holiday.getType().toUpperCase().trim());
        if (holiday.getActive() == null) holiday.setActive(true);

        log.info("Adding holiday '{}' ({}) on {} to list {}", holiday.getName(), holiday.getType(), holiday.getDate(), list.getName());
        return holidayRepository.save(holiday);
    }

    @Transactional
    @CacheEvict(value = {"holidays_published", "attendance_calendar"}, allEntries = true)
    public Holiday updateHoliday(Long holidayId, Holiday updated) {
        Holiday existing = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found with id: " + holidayId));

        HolidayList list = holidayListRepository.findById(existing.getHolidayListId())
                .orElseThrow(() -> new IllegalStateException("Associated holiday list missing"));

        if (updated.getName() != null && !updated.getName().isBlank()) {
            existing.setName(updated.getName().trim());
        }

        if (updated.getDate() != null && !updated.getDate().equals(existing.getDate())) {
            if (updated.getDate().getYear() != list.getYear()) {
                throw new IllegalArgumentException("Holiday date (" + updated.getDate() + ") does not match list year (" + list.getYear() + ")");
            }
            if (holidayRepository.existsByHolidayListIdAndDateAndIdNot(existing.getHolidayListId(), updated.getDate(), holidayId)) {
                throw new IllegalArgumentException("Another holiday already exists on " + updated.getDate() + " in this list");
            }
            existing.setDate(updated.getDate());
        }

        if (updated.getType() != null && !updated.getType().isBlank()) {
            String newType = updated.getType().toUpperCase().trim();
            if (!newType.equals("GENERAL") && !newType.equals("RESTRICTED")) {
                throw new IllegalArgumentException("Holiday type must be GENERAL or RESTRICTED");
            }
            existing.setType(newType);
        }

        if (updated.getDescription() != null) {
            existing.setDescription(updated.getDescription());
        }

        if (updated.getActive() != null) {
            existing.setActive(updated.getActive());
        }

        return holidayRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = {"holidays_published", "attendance_calendar"}, allEntries = true)
    public void deleteHoliday(Long holidayId) {
        Holiday existing = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found with id: " + holidayId));
        holidayRepository.delete(existing);
        log.info("Deleted holiday id: {} ('{}')", holidayId, existing.getName());
    }

    // ==========================================
    // EMPLOYEE: Holiday Calendar & RH Status
    // ==========================================

    @Transactional(readOnly = true)
    public EmployeeHolidayCalendarDto getEmployeeHolidayCalendar(Integer year, Long employeeId) {
        List<Holiday> publishedHolidays = holidayRepository.findPublishedHolidaysByYear(year);

        // Fetch employee's existing leave requests for RESTRICTED_HOLIDAY in this year
        Map<LocalDate, LeaveRequest> rhRequestsByDate = new HashMap<>();
        if (employeeId != null) {
            LocalDate startOfYear = LocalDate.of(year, 1, 1);
            LocalDate endOfYear = LocalDate.of(year, 12, 31);
            List<LeaveRequest> empLeaves = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
            for (LeaveRequest lr : empLeaves) {
                if (lr.getLeaveType() != null && lr.getLeaveType().toUpperCase().contains("RESTRICTED")) {
                    if (!lr.getStartDate().isBefore(startOfYear) && !lr.getStartDate().isAfter(endOfYear)) {
                        rhRequestsByDate.putIfAbsent(lr.getStartDate(), lr);
                    }
                }
            }
        }

        List<HolidayDto> dtoList = new ArrayList<>();
        for (Holiday h : publishedHolidays) {
            HolidayDto dto = HolidayDto.builder()
                    .id(h.getId())
                    .holidayListId(h.getHolidayListId())
                    .name(h.getName())
                    .date(h.getDate())
                    .type(h.getType())
                    .description(h.getDescription())
                    .active(h.getActive())
                    .build();

            if ("GENERAL".equalsIgnoreCase(h.getType())) {
                dto.setStatus("HOLIDAY");
            } else {
                LeaveRequest rhReq = rhRequestsByDate.get(h.getDate());
                if (rhReq != null) {
                    dto.setStatus(rhReq.getStatus().toUpperCase()); // PENDING, APPROVED, REJECTED
                    dto.setLeaveRequestId(rhReq.getId());
                } else {
                    dto.setStatus("NOT_APPLIED");
                }
            }
            dtoList.add(dto);
        }

        // Fetch or create LeaveBalance for RH quota display
        double rhQuota = 2.0;
        double rhUsed = 0.0;
        double rhRemaining = 2.0;

        if (employeeId != null) {
            LeaveBalance balance = leaveService.getOrCreateLeaveBalance(employeeId, year);
            rhQuota = balance.getRestrictedHolidayQuota() != null ? balance.getRestrictedHolidayQuota() : 2.0;
            rhUsed = balance.getRestrictedHolidayUsed() != null ? balance.getRestrictedHolidayUsed() : 0.0;
            rhRemaining = balance.getRestrictedHolidayRemaining();
        }

        return EmployeeHolidayCalendarDto.builder()
                .year(year)
                .restrictedHolidayQuota(rhQuota)
                .restrictedHolidayUsed(rhUsed)
                .restrictedHolidayRemaining(rhRemaining)
                .holidays(dtoList)
                .build();
    }

    // ==========================================
    // ATTENDANCE & LEAVE SYSTEM HELPERS
    // ==========================================

    @Transactional(readOnly = true)
    public boolean isGeneralHoliday(LocalDate date) {
        List<Holiday> list = holidayRepository.findActiveGeneralHolidayOnDate(date);
        return !list.isEmpty();
    }

    @Transactional(readOnly = true)
    public Optional<Holiday> getGeneralHolidayOnDate(LocalDate date) {
        List<Holiday> list = holidayRepository.findActiveGeneralHolidayOnDate(date);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Transactional(readOnly = true)
    public List<Holiday> getGeneralHolidaysBetween(LocalDate start, LocalDate end) {
        return holidayRepository.findActiveGeneralHolidaysBetween(start, end);
    }

    @Transactional(readOnly = true)
    public Optional<Holiday> getActiveRestrictedHolidayOnDate(LocalDate date) {
        return holidayRepository.findActiveRestrictedHolidayOnDate(date);
    }

    // ==========================================
    // MAPPERS
    // ==========================================

    private HolidayListDto mapToListDto(HolidayList list) {
        List<Holiday> holidays = holidayRepository.findByHolidayListIdOrderByDateAsc(list.getId());
        int generalCount = 0;
        int restrictedCount = 0;
        List<HolidayDto> holidayDtos = new ArrayList<>();

        for (Holiday h : holidays) {
            if ("GENERAL".equalsIgnoreCase(h.getType())) generalCount++;
            if ("RESTRICTED".equalsIgnoreCase(h.getType())) restrictedCount++;

            holidayDtos.add(HolidayDto.builder()
                    .id(h.getId())
                    .holidayListId(h.getHolidayListId())
                    .name(h.getName())
                    .date(h.getDate())
                    .type(h.getType())
                    .description(h.getDescription())
                    .active(h.getActive())
                    .build());
        }

        return HolidayListDto.builder()
                .id(list.getId())
                .name(list.getName())
                .year(list.getYear())
                .description(list.getDescription())
                .applicableGroup(list.getApplicableGroup())
                .active(list.getActive())
                .published(list.getPublished())
                .createdBy(list.getCreatedBy())
                .createdAt(list.getCreatedAt())
                .updatedAt(list.getUpdatedAt())
                .totalHolidays(holidays.size())
                .generalHolidaysCount(generalCount)
                .restrictedHolidaysCount(restrictedCount)
                .holidays(holidayDtos)
                .build();
    }
}
