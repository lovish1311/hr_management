package com.example.hr_management_backend.features.leaves;

import com.example.hr_management_backend.features.holidays.model.Holiday;
import com.example.hr_management_backend.features.holidays.repository.HolidayRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.example.hr_management_backend.features.holidays.model.HolidayList;
import com.example.hr_management_backend.features.holidays.repository.HolidayListRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class SandwichLeavePolicyTest {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private HolidayListRepository holidayListRepository;

    private final Long employeeId = 2L; // Lovish Kumar (existing in seed)

    @BeforeEach
    public void setup() {
        holidayRepository.deleteAll();

        // Fetch existing holiday list for 2026 or create if not exists
        HolidayList hl;
        java.util.List<HolidayList> lists = holidayListRepository.findAll();
        if (!lists.isEmpty()) {
            hl = lists.get(0);
            hl.setPublished(true);
            hl.setActive(true);
            holidayListRepository.save(hl);
        } else {
            hl = HolidayList.builder()
                    .name("Test Holiday List")
                    .year(2026)
                    .active(true)
                    .published(true)
                    .build();
            hl = holidayListRepository.save(hl);
        }
        
        Holiday gh = Holiday.builder()
                .name("Test General Holiday")
                .date(LocalDate.of(2026, 11, 11))
                .type("GENERAL")
                .active(true)
                .holidayListId(hl.getId())
                .build();
        holidayRepository.save(gh);
    }

    @Test
    @DisplayName("1 & 5. Friday -> Monday (Weekend-containing multi-day leave = weekend counted)")
    public void testWeekendIncludedInSandwichLeave() {
        // Nov 6 (Fri) to Nov 9 (Mon)
        LeaveRequest req = LeaveRequest.builder()
                .employeeId(employeeId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 11, 6))
                .endDate(LocalDate.of(2026, 11, 9))
                .build();

        LeaveRequest saved = leaveService.applyForLeave(req);
        
        // 6th, 7th (Sat), 8th (Sun), 9th = 4 days
        assertEquals(4.0, saved.getTotalDays(), "Friday to Monday should be 4 days, including the weekend");
    }

    @Test
    @DisplayName("2 & 4. Monday -> Friday containing a General Holiday = 5 days")
    public void testHolidayIncludedInSandwichLeave() {
        // Nov 9 (Mon) to Nov 13 (Fri), with Nov 11 (Wed) as a General Holiday
        LeaveRequest req = LeaveRequest.builder()
                .employeeId(employeeId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 11, 9))
                .endDate(LocalDate.of(2026, 11, 13))
                .build();

        LeaveRequest saved = leaveService.applyForLeave(req);
        
        // 9th to 13th inclusive is 5 days
        assertEquals(5.0, saved.getTotalDays(), "Monday to Friday should be 5 days, including the general holiday");
    }

    @Test
    @DisplayName("3. Standalone General Holiday = rejected")
    public void testStandaloneGeneralHolidayRejected() {
        // Nov 11 (Wed) is a General Holiday
        LeaveRequest req = LeaveRequest.builder()
                .employeeId(employeeId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 11, 11))
                .endDate(LocalDate.of(2026, 11, 11))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveService.applyForLeave(req));
        assertTrue(ex.getMessage().contains("is already an official company holiday"), 
                   "Standalone general holiday should be rejected");
    }

    @Test
    @DisplayName("6. Separate leave requests remain separate and are each calculated independently")
    public void testSeparateLeavesNotSandwiched() {
        // E.g., Employee applies for Friday, and a separate request for Monday
        // They should just be 1 day each.
        
        LeaveRequest fridayReq = LeaveRequest.builder()
                .employeeId(employeeId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 11, 6)) // Friday
                .endDate(LocalDate.of(2026, 11, 6))
                .build();

        LeaveRequest mondayReq = LeaveRequest.builder()
                .employeeId(employeeId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 11, 9)) // Monday
                .endDate(LocalDate.of(2026, 11, 9))
                .build();

        LeaveRequest savedFri = leaveService.applyForLeave(fridayReq);
        LeaveRequest savedMon = leaveService.applyForLeave(mondayReq);

        assertEquals(1.0, savedFri.getTotalDays());
        assertEquals(1.0, savedMon.getTotalDays());
    }
}
