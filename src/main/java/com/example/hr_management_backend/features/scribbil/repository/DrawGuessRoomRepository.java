package com.example.hr_management_backend.features.scribbil.repository;

import com.example.hr_management_backend.features.scribbil.model.DrawGuessGameState;
import com.example.hr_management_backend.features.scribbil.model.DrawGuessRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrawGuessRoomRepository extends JpaRepository<DrawGuessRoom, Long> {

    Optional<DrawGuessRoom> findByRoomCode(String roomCode);

    Optional<DrawGuessRoom> findByRoomCodeAndStateNot(String roomCode, DrawGuessGameState state);

    List<DrawGuessRoom> findByState(DrawGuessGameState state);

    List<DrawGuessRoom> findByHostEmployeeId(Long hostEmployeeId);

    @Query("SELECT r FROM DrawGuessRoom r WHERE r.state IN ('LOBBY', 'STARTING', 'WORD_SELECTION', 'DRAWING', 'ROUND_RESULT') ORDER BY r.createdAt DESC")
    List<DrawGuessRoom> findActiveRooms();
}
