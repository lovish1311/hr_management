package com.example.hr_management_backend.features.employees.repository;

import com.example.hr_management_backend.features.employees.model.StarredPeer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StarredPeerRepository extends JpaRepository<StarredPeer, Long> {
    List<StarredPeer> findByStarrerEmployeeId(Long starrerEmployeeId);
    Optional<StarredPeer> findByStarrerEmployeeIdAndStarredEmployeeId(Long starrerEmployeeId, Long starredEmployeeId);
    void deleteByStarrerEmployeeIdAndStarredEmployeeId(Long starrerEmployeeId, Long starredEmployeeId);
}
