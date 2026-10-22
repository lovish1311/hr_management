package com.example.hr_management_backend.features.notifications.repository;

import com.example.hr_management_backend.features.notifications.model.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    @Query("SELECT d.token FROM DeviceToken d WHERE d.employeeId = :employeeId")
    List<String> findTokensByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT d.token FROM DeviceToken d WHERE d.employeeId IN :employeeIds")
    List<String> findTokensByEmployeeIds(@Param("employeeIds") Collection<Long> employeeIds);

    @Query("SELECT d.token FROM DeviceToken d")
    List<String> findAllActiveTokens();

    @Modifying
    @Query("DELETE FROM DeviceToken d WHERE d.token IN :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);

    @Modifying
    @Query("DELETE FROM DeviceToken d WHERE d.token = :token")
    int deleteByToken(@Param("token") String token);
}
