package com.example.hr_management_backend.features.employees.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "starred_peers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"starrerEmployeeId", "starredEmployeeId"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StarredPeer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long starrerEmployeeId;

    @Column(nullable = false)
    private Long starredEmployeeId;
}
