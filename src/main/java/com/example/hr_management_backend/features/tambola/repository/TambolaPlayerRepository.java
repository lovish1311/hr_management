package com.example.hr_management_backend.features.tambola.repository;

import com.example.hr_management_backend.features.tambola.model.TambolaPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TambolaPlayerRepository extends JpaRepository<TambolaPlayer, Long> {

    List<TambolaPlayer> findByGameId(Long gameId);

    List<TambolaPlayer> findByGameIdOrderByJoinedAtAsc(Long gameId);

    Optional<TambolaPlayer> findByGameIdAndEmployeeId(Long gameId, Long employeeId);

    boolean existsByGameIdAndEmployeeId(Long gameId, Long employeeId);

    long countByGameId(Long gameId);
}
