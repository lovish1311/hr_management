package com.example.hr_management_backend.features.scribbil.repository;

import com.example.hr_management_backend.features.scribbil.model.DrawGuessScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DrawGuessScoreRepository extends JpaRepository<DrawGuessScore, Long> {

    List<DrawGuessScore> findByRoomCode(String roomCode);

    List<DrawGuessScore> findByEmployeeId(Long employeeId);
}
