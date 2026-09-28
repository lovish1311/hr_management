package com.example.hr_management_backend.features.holidays.repository;

import com.example.hr_management_backend.features.holidays.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByHolidayListIdOrderByDateAsc(Long holidayListId);

    List<Holiday> findByHolidayListIdAndActiveTrueOrderByDateAsc(Long holidayListId);

    Optional<Holiday> findByHolidayListIdAndDate(Long holidayListId, LocalDate date);

    boolean existsByHolidayListIdAndDate(Long holidayListId, LocalDate date);

    boolean existsByHolidayListIdAndDateAndIdNot(Long holidayListId, LocalDate date, Long id);

    /**
     * Efficiently fetches all active holidays for published holiday lists belonging to a specific year.
     */
    @Query("SELECT h FROM Holiday h WHERE h.active = true AND h.holidayListId IN " +
           "(SELECT hl.id FROM HolidayList hl WHERE hl.year = :year AND hl.published = true AND hl.active = true) " +
           "ORDER BY h.date ASC")
    List<Holiday> findPublishedHolidaysByYear(@Param("year") Integer year);

    /**
     * Finds active General Holidays within a date range for published lists.
     */
    @Query("SELECT h FROM Holiday h WHERE h.active = true AND h.type = 'GENERAL' " +
           "AND h.date BETWEEN :startDate AND :endDate " +
           "AND h.holidayListId IN (SELECT hl.id FROM HolidayList hl WHERE hl.published = true AND hl.active = true)")
    List<Holiday> findActiveGeneralHolidaysBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Checks if a specific date is an active published General Holiday.
     */
    @Query("SELECT h FROM Holiday h WHERE h.active = true AND h.type = 'GENERAL' " +
           "AND h.date = :date " +
           "AND h.holidayListId IN (SELECT hl.id FROM HolidayList hl WHERE hl.published = true AND hl.active = true)")
    List<Holiday> findActiveGeneralHolidayOnDate(@Param("date") LocalDate date);

    /**
     * Finds an active published Restricted Holiday on a specific date.
     */
    @Query("SELECT h FROM Holiday h WHERE h.active = true AND h.type = 'RESTRICTED' " +
           "AND h.date = :date " +
           "AND h.holidayListId IN (SELECT hl.id FROM HolidayList hl WHERE hl.published = true AND hl.active = true)")
    Optional<Holiday> findActiveRestrictedHolidayOnDate(@Param("date") LocalDate date);
}
