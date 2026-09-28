package com.example.hr_management_backend.features.holidays.repository;

import com.example.hr_management_backend.features.holidays.model.HolidayList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayListRepository extends JpaRepository<HolidayList, Long> {

    List<HolidayList> findByYearOrderByCreatedAtDesc(Integer year);

    Optional<HolidayList> findFirstByYearAndActiveTrueOrderByCreatedAtDesc(Integer year);

    Optional<HolidayList> findFirstByYearAndPublishedTrueAndActiveTrueOrderByCreatedAtDesc(Integer year);

    boolean existsByYearAndName(Integer year, String name);

    boolean existsByYearAndNameAndIdNot(Integer year, String name, Long id);
}
