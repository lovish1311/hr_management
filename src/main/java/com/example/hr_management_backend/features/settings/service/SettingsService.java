package com.example.hr_management_backend.features.settings.service;

import com.example.hr_management_backend.features.settings.model.Setting;
import com.example.hr_management_backend.features.settings.repository.SettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SettingsService {

    private final SettingRepository settingRepository;

    @Autowired
    public SettingsService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @PostConstruct
    public void init() {
        if (settingRepository.count() == 0) {
            settingRepository.save(new Setting("theme", "light"));
            settingRepository.save(new Setting("companyName", "Default Corp"));
            settingRepository.save(new Setting("workHoursStart", "09:00"));
            settingRepository.save(new Setting("workHoursEnd", "17:00"));
        }
    }

    public String getSetting(String key) {
        return settingRepository.findById(key)
                .map(Setting::getKeyValue)
                .orElse("");
    }

    public void updateSetting(String key, String value) {
        settingRepository.save(new Setting(key, value));
    }

    public Map<String, String> getAllSettings() {
        List<Setting> settings = settingRepository.findAll();
        Map<String, String> map = new HashMap<>();
        for (Setting s : settings) {
            map.put(s.getKeyName(), s.getKeyValue());
        }
        return map;
    }
}
