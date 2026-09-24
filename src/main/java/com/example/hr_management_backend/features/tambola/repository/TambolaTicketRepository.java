package com.example.hr_management_backend.features.tambola.repository;

import com.example.hr_management_backend.features.tambola.model.TambolaTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TambolaTicketRepository extends JpaRepository<TambolaTicket, Long> {

    Optional<TambolaTicket> findByGameIdAndEmployeeId(Long gameId, Long employeeId);

    List<TambolaTicket> findByGameId(Long gameId);
}
