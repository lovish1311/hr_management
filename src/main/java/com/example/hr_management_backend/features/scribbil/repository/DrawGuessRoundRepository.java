package com.example.hr_management_backend.features.scribbil.repository;

import com.example.hr_management_backend.features.scribbil.model.DrawGuessRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrawGuessRoundRepository extends JpaRepository<DrawGuessRound, Long> {

    List<DrawGuessRound> findByRoomCodeOrderByRoundNumberAscTurnNumberAsc(String roomCode);

    Optional<DrawGuessRound> findByRoomCodeAndRoundNumberAndTurnNumber(String roomCode, Integer roundNumber, Integer turnNumber);
}
