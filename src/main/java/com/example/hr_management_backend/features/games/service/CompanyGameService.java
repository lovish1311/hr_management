package com.example.hr_management_backend.features.games.service;

import com.example.hr_management_backend.features.games.model.CompanyGame;

import java.util.List;

public interface CompanyGameService {
    List<CompanyGame> getAllGames();
    CompanyGame getGameByKey(String gameKey);
    CompanyGame updateGameStatus(String gameKey, boolean isEnabled);
    CompanyGame updateGameAccess(String gameKey, String allowedRoles);
    boolean isGameAvailableForRole(String gameKey, String role);
}
