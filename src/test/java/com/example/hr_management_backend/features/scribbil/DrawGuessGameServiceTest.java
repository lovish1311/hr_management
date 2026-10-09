package com.example.hr_management_backend.features.scribbil;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.scribbil.dto.*;
import com.example.hr_management_backend.features.scribbil.model.*;
import com.example.hr_management_backend.features.scribbil.repository.*;
import com.example.hr_management_backend.features.scribbil.service.*;
import com.example.hr_management_backend.features.scribbil.websocket.DrawGuessWebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawGuessGameServiceTest {

    @Mock private DrawGuessRoomRepository roomRepository;
    @Mock private DrawGuessPlayerRepository playerRepository;
    @Mock private DrawGuessRoundRepository roundRepository;
    @Mock private DrawGuessScoreRepository scoreRepository;
    @Mock private WordDictionaryService wordDictionaryService;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DrawGuessWebSocketSessionManager sessionManager;
    @Mock private DrawGuessScoringEngine scoringEngine;

    @InjectMocks
    private DrawGuessGameServiceImpl gameService;

    private Employee hostEmployee;
    private Employee guesserEmployee;

    @BeforeEach
    void setUp() {
        hostEmployee = Employee.builder()
                .id(1L)
                .firstName("Lovish")
                .lastName("Kumar")
                .email("lovish@company.com")
                .role("SUPER_ADMIN")
                .build();

        guesserEmployee = Employee.builder()
                .id(2L)
                .firstName("Rahul")
                .lastName("Sharma")
                .email("rahul@company.com")
                .role("EMPLOYEE")
                .build();
    }

    @Test
    @DisplayName("Should create Draw & Guess room and register host player")
    void testCreateRoom() {
        when(employeeRepository.findByEmail("lovish@company.com")).thenReturn(Optional.of(hostEmployee));
        when(roomRepository.findByRoomCode(anyString())).thenReturn(Optional.empty());
        when(roomRepository.save(any(DrawGuessRoom.class))).thenAnswer(i -> {
            DrawGuessRoom r = i.getArgument(0);
            r.setId(10L);
            return r;
        });
        when(playerRepository.save(any(DrawGuessPlayer.class))).thenAnswer(i -> i.getArgument(0));

        CreateDrawGuessRoomRequest req = CreateDrawGuessRoomRequest.builder()
                .roomName("Engineering Scribble")
                .maxRounds(3)
                .drawTimeSeconds(60)
                .category("TECH")
                .build();

        DrawGuessRoomResponse res = gameService.createRoom(req, "lovish@company.com");

        assertNotNull(res);
        assertNotNull(res.getRoomCode());
        assertEquals("Engineering Scribble", res.getRoomName());
        assertEquals(DrawGuessGameState.LOBBY, res.getState());
        assertEquals(1, res.getPlayers().size());
        assertTrue(res.getPlayers().get(0).getIsHost());
    }

    @Test
    @DisplayName("Should allow employee to join room")
    void testJoinRoom() {
        DrawGuessRoom room = DrawGuessRoom.builder()
                .id(10L)
                .roomCode("DG1234")
                .hostEmployeeId(1L)
                .state(DrawGuessGameState.LOBBY)
                .maxRounds(3)
                .drawTimeSeconds(60)
                .build();

        DrawGuessPlayer hostPlayer = DrawGuessPlayer.builder()
                .id(1L)
                .roomCode("DG1234")
                .employeeId(1L)
                .employeeName("Lovish Kumar")
                .isHost(true)
                .isConnected(true)
                .turnOrder(0)
                .build();

        DrawGuessPlayer guesserPlayer = DrawGuessPlayer.builder()
                .id(2L)
                .roomCode("DG1234")
                .employeeId(2L)
                .employeeName("Rahul Sharma")
                .isHost(false)
                .isConnected(true)
                .turnOrder(1)
                .build();

        when(roomRepository.findByRoomCode("DG1234")).thenReturn(Optional.of(room));
        when(employeeRepository.findByEmail("rahul@company.com")).thenReturn(Optional.of(guesserEmployee));
        when(playerRepository.findByRoomCodeAndEmployeeId("DG1234", 2L)).thenReturn(Optional.empty());
        when(playerRepository.countByRoomCode("DG1234")).thenReturn(1L);
        when(playerRepository.save(any(DrawGuessPlayer.class))).thenReturn(guesserPlayer);
        when(playerRepository.findByRoomCodeOrderByTurnOrderAsc("DG1234")).thenReturn(List.of(hostPlayer, guesserPlayer));

        JoinDrawGuessRoomRequest req = JoinDrawGuessRoomRequest.builder().roomCode("DG1234").build();
        DrawGuessRoomResponse res = gameService.joinRoom(req, "rahul@company.com");

        assertNotNull(res);
        assertEquals(2, res.getPlayers().size());
        verify(sessionManager).broadcast(eq("DG1234"), any());
    }

    @Test
    @DisplayName("Should detect exact guess match and award points")
    void testSubmitGuessCorrect() {
        DrawGuessRoom room = DrawGuessRoom.builder()
                .id(10L)
                .roomCode("DG1234")
                .hostEmployeeId(1L)
                .activeDrawerEmployeeId(1L)
                .currentWord("Elephant")
                .state(DrawGuessGameState.DRAWING)
                .drawTimeSeconds(80)
                .build();

        DrawGuessPlayer player = DrawGuessPlayer.builder()
                .id(2L)
                .roomCode("DG1234")
                .employeeId(2L)
                .employeeName("Rahul Sharma")
                .score(0)
                .hasGuessedCorrectly(false)
                .isConnected(true)
                .build();

        when(roomRepository.findByRoomCode("DG1234")).thenReturn(Optional.of(room));
        when(employeeRepository.findByEmail("rahul@company.com")).thenReturn(Optional.of(guesserEmployee));
        when(playerRepository.findByRoomCodeAndEmployeeId("DG1234", 2L)).thenReturn(Optional.of(player));
        when(playerRepository.findByRoomCodeAndIsConnectedTrue("DG1234")).thenReturn(List.of(player));
        when(scoringEngine.calculateGuessScore(anyDouble(), anyDouble())).thenReturn(400);

        GuessResultDto result = gameService.submitGuess("DG1234", "elephant", "rahul@company.com");

        assertNotNull(result);
        assertTrue(result.getIsCorrect());
        assertTrue(result.getPointsAwarded() > 0);
        assertTrue(player.getHasGuessedCorrectly());
        verify(scoreRepository).save(any(DrawGuessScore.class));
    }
}
