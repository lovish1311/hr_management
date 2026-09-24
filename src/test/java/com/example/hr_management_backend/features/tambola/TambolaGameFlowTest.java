package com.example.hr_management_backend.features.tambola;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.tambola.dto.*;
import com.example.hr_management_backend.features.tambola.model.TambolaDraw;
import com.example.hr_management_backend.features.tambola.model.TambolaGame;
import com.example.hr_management_backend.features.tambola.repository.TambolaDrawRepository;
import com.example.hr_management_backend.features.tambola.repository.TambolaGameRepository;
import com.example.hr_management_backend.features.tambola.service.TambolaGameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class TambolaGameFlowTest {

    @Autowired
    private TambolaGameService gameService;

    @Autowired
    private TambolaGameRepository gameRepository;

    @Autowired
    private TambolaDrawRepository drawRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee host;
    private Employee player;

    @BeforeEach
    void setUp() {
        host = Employee.builder()
                .firstName("Tambola")
                .lastName("Host")
                .email("host_" + UUID.randomUUID() + "@example.com")
                .employeeCode("EMP_" + UUID.randomUUID().toString().substring(0, 8))
                .role("SUPER_ADMIN")
                .hasTambolaAccess(true)
                .build();
        host = employeeRepository.save(host);

        player = Employee.builder()
                .firstName("Tambola")
                .lastName("Player")
                .email("player_" + UUID.randomUUID() + "@example.com")
                .employeeCode("EMP_" + UUID.randomUUID().toString().substring(0, 8))
                .role("EMPLOYEE")
                .hasTambolaAccess(false)
                .build();
        player = employeeRepository.save(player);
    }

    @Test
    @DisplayName("Create game, join players, generate tickets, and start game")
    void testGameLifecycle() {
        CreateTambolaGameDto createDto = new CreateTambolaGameDto();
        createDto.setTitle("Engineering Friday Tambola");

        TambolaGameResponseDto game = gameService.createGame(host.getId(), createDto);
        assertNotNull(game);
        assertNotNull(game.getRoomCode());
        assertEquals("WAITING", game.getStatus());
        assertEquals("Engineering Friday Tambola", game.getTitle());

        // Player joins
        TambolaTicketDto ticket = gameService.joinGame(player.getId(), game.getRoomCode());
        assertNotNull(ticket);
        assertEquals(3, ticket.getGrid().size());
        assertEquals(9, ticket.getGrid().get(0).size());
        assertEquals(5, ticket.getRow1().size());
        assertEquals(5, ticket.getRow2().size());
        assertEquals(5, ticket.getRow3().size());
        assertEquals(15, ticket.getAllNumbers().size());

        // Host starts game
        TambolaGameResponseDto started = gameService.startGame(host.getId(), game.getRoomCode());
        assertEquals("RUNNING", started.getStatus());

        // Host draws a number
        TambolaDrawResponseDto draw = gameService.drawNextNumber(host.getId(), game.getRoomCode());
        assertNotNull(draw);
        assertTrue(draw.getNumber() >= 1 && draw.getNumber() <= 90);
        assertEquals(1, draw.getDrawOrder());
    }

    @Test
    @DisplayName("Bogus claims are rejected with BAD_REQUEST (400)")
    void testBogusClaimRejection() {
        CreateTambolaGameDto createDto = new CreateTambolaGameDto();
        createDto.setTitle("Claim Test Game");
        TambolaGameResponseDto game = gameService.createGame(host.getId(), createDto);

        TambolaTicketDto ticket = gameService.joinGame(player.getId(), game.getRoomCode());
        gameService.startGame(host.getId(), game.getRoomCode());

        // Try to claim FIRST_HOUSE without any drawn numbers
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                gameService.claimPrize(player.getId(), game.getRoomCode(), "FIRST_HOUSE")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Bogus claim"));
    }

    @Test
    @DisplayName("Valid claim succeeds and duplicate claim throws CONFLICT (409)")
    void testValidClaimAndDuplicateRejection() {
        CreateTambolaGameDto createDto = new CreateTambolaGameDto();
        createDto.setTitle("Valid Claim Game");
        TambolaGameResponseDto gameDto = gameService.createGame(host.getId(), createDto);

        TambolaTicketDto ticket = gameService.joinGame(player.getId(), gameDto.getRoomCode());
        gameService.startGame(host.getId(), gameDto.getRoomCode());

        TambolaGame game = gameRepository.findByRoomCode(gameDto.getRoomCode()).orElseThrow();

        // Simulate drawing all row 1 numbers for this player
        List<Integer> row1 = ticket.getRow1();
        int order = 1;
        for (int num : row1) {
            TambolaDraw draw = TambolaDraw.builder()
                    .gameId(game.getId())
                    .drawOrder(order++)
                    .number(num)
                    .build();
            drawRepository.save(draw);
        }
        game.setTotalNumbersDrawn(order - 1);
        game.setLastDrawnNumber(row1.get(row1.size() - 1));
        gameRepository.save(game);

        // Player claims FIRST_HOUSE -> should succeed!
        TambolaClaimResultDto result = gameService.claimPrize(player.getId(), gameDto.getRoomCode(), "FIRST_HOUSE");
        assertTrue(result.isSuccess());
        assertEquals("FIRST_HOUSE", result.getPrizeType());
        assertEquals(player.getId(), result.getWinnerEmployeeId());

        // Second claim attempt for FIRST_HOUSE -> MUST throw CONFLICT (409)
        ResponseStatusException conflictEx = assertThrows(ResponseStatusException.class, () ->
                gameService.claimPrize(host.getId(), gameDto.getRoomCode(), "FIRST_HOUSE")
        );
        assertEquals(HttpStatus.CONFLICT, conflictEx.getStatusCode());
        assertTrue(conflictEx.getReason().contains("already been claimed"));
    }
}
