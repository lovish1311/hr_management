package com.example.hr_management_backend.features.scribbil.repository;

import com.example.hr_management_backend.features.scribbil.model.DrawGuessPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrawGuessPlayerRepository extends JpaRepository<DrawGuessPlayer, Long> {

    List<DrawGuessPlayer> findByRoomCodeOrderByTurnOrderAsc(String roomCode);

    Optional<DrawGuessPlayer> findByRoomCodeAndEmployeeId(String roomCode, Long employeeId);

    List<DrawGuessPlayer> findByRoomCodeAndIsConnectedTrue(String roomCode);

    void deleteByRoomCode(String roomCode);

    long countByRoomCode(String roomCode);
}
