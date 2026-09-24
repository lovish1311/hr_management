package com.example.hr_management_backend.features.games.controller;

import com.example.hr_management_backend.features.games.model.CompanyGame;
import com.example.hr_management_backend.features.games.service.CompanyGameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final CompanyGameService companyGameService;

    @GetMapping
    public ResponseEntity<List<CompanyGame>> getAllGames() {
        return ResponseEntity.ok(companyGameService.getAllGames());
    }

    @GetMapping("/{gameKey}")
    public ResponseEntity<CompanyGame> getGameByKey(@PathVariable String gameKey) {
        return ResponseEntity.ok(companyGameService.getGameByKey(gameKey));
    }

    @PatchMapping("/{gameKey}/status")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CompanyGame> updateGameStatus(
            @PathVariable String gameKey,
            @RequestParam boolean isEnabled) {
        return ResponseEntity.ok(companyGameService.updateGameStatus(gameKey, isEnabled));
    }

    @PatchMapping("/{gameKey}/access")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CompanyGame> updateGameAccess(
            @PathVariable String gameKey,
            @RequestParam String allowedRoles) {
        return ResponseEntity.ok(companyGameService.updateGameAccess(gameKey, allowedRoles));
    }
}
