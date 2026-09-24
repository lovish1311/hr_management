package com.example.hr_management_backend.features.tambola.repository;

import com.example.hr_management_backend.features.tambola.model.TambolaGame;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TambolaGameRepository extends JpaRepository<TambolaGame, Long> {

    Optional<TambolaGame> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM TambolaGame g WHERE g.id = :id")
    Optional<TambolaGame> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM TambolaGame g WHERE g.roomCode = :roomCode")
    Optional<TambolaGame> findByRoomCodeWithLock(@Param("roomCode") String roomCode);

    List<TambolaGame> findAllByOrderByCreatedAtDesc();

    List<TambolaGame> findByStatusInOrderByCreatedAtDesc(List<String> statuses);
}
