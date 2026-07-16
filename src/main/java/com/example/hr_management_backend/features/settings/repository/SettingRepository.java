package com.example.hr_management_backend.features.settings.repository;

import com.example.hr_management_backend.features.settings.model.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettingRepository extends JpaRepository<Setting, String> {
}
