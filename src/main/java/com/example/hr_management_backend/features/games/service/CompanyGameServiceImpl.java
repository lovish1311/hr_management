package com.example.hr_management_backend.features.games.service;

import com.example.hr_management_backend.features.games.model.CompanyGame;
import com.example.hr_management_backend.features.games.repository.CompanyGameRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyGameServiceImpl implements CompanyGameService {

    private final CompanyGameRepository companyGameRepository;

    @PostConstruct
    public void initDefaultGames() {
        if (!companyGameRepository.existsById("TAMBOLA")) {
            CompanyGame tambola = CompanyGame.builder()
                    .gameKey("TAMBOLA")
                    .title("Tambola Housie")
                    .description("Classic Indian 90-ball multiplayer Housie with live sync caller board, smart ticket daubing, and real-time prize claims.")
                    .category("Multiplayer Social")
                    .iconName("confirmation_number_rounded")
                    .gradientStart("#312E81")
                    .gradientEnd("#4338CA")
                    .isEnabled(true)
                    .allowedRoles("ROLE_EMPLOYEE,ROLE_HR_ADMIN,ROLE_SUPER_ADMIN")
                    .minPlayers(2)
                    .maxPlayers(100)
                    .build();
            companyGameRepository.save(tambola);
            log.info("Initialized default game: TAMBOLA in CompanyGame registry");
        }
    }

    @Override
    public List<CompanyGame> getAllGames() {
        return companyGameRepository.findAll();
    }

    @Override
    public CompanyGame getGameByKey(String gameKey) {
        return companyGameRepository.findByGameKey(gameKey.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Game not found: " + gameKey));
    }

    @Override
    @Transactional
    public CompanyGame updateGameStatus(String gameKey, boolean isEnabled) {
        CompanyGame game = getGameByKey(gameKey);
        game.setIsEnabled(isEnabled);
        return companyGameRepository.save(game);
    }

    @Override
    @Transactional
    public CompanyGame updateGameAccess(String gameKey, String allowedRoles) {
        CompanyGame game = getGameByKey(gameKey);
        game.setAllowedRoles(allowedRoles);
        return companyGameRepository.save(game);
    }

    @Override
    public boolean isGameAvailableForRole(String gameKey, String role) {
        return companyGameRepository.findByGameKey(gameKey.toUpperCase())
                .map(game -> {
                    if (!Boolean.TRUE.equals(game.getIsEnabled())) return false;
                    String roles = game.getAllowedRoles();
                    if (roles == null || roles.isBlank() || roles.equalsIgnoreCase("ALL")) return true;
                    return roles.contains(role);
                })
                .orElse(false);
    }
}
