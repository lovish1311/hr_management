package com.example.hr_management_backend.features.tambola.repository;

import com.example.hr_management_backend.features.tambola.model.TambolaWinner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TambolaWinnerRepository extends JpaRepository<TambolaWinner, Long> {

    List<TambolaWinner> findByGameIdOrderByClaimedAtAsc(Long gameId);

    boolean existsByGameIdAndPrizeType(Long gameId, String prizeType);

    Optional<TambolaWinner> findByGameIdAndPrizeTypeAndPrizeRank(Long gameId, String prizeType, int prizeRank);

    void deleteByGameId(Long gameId);
}
