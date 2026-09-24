package com.example.hr_management_backend.features.tambola.repository;

import com.example.hr_management_backend.features.tambola.model.TambolaDraw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TambolaDrawRepository extends JpaRepository<TambolaDraw, Long> {

    List<TambolaDraw> findByGameIdOrderByDrawOrderAsc(Long gameId);

    boolean existsByGameIdAndNumber(Long gameId, int number);

    Optional<TambolaDraw> findTopByGameIdOrderByDrawOrderDesc(Long gameId);

    long countByGameId(Long gameId);

    void deleteByGameId(Long gameId);
}
