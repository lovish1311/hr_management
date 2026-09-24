package com.example.hr_management_backend.features.games.repository;

import com.example.hr_management_backend.features.games.model.CompanyGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyGameRepository extends JpaRepository<CompanyGame, String> {
    Optional<CompanyGame> findByGameKey(String gameKey);
}
