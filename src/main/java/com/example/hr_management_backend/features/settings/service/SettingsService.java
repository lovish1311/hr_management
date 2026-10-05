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
        saveOrUpdateDefault("theme", "light");
        saveOrUpdateDefault("companyName", "Default Corp");
        saveOrUpdateDefault("workHoursStart", "10:00");
        saveOrUpdateDefault("workHoursEnd", "19:00");
        saveOrUpdateDefault("shiftStartTime", "10:00");
        saveOrUpdateDefault("shiftEndTime", "19:00");
        saveOrUpdateDefault("lunchStartTime", "14:00");
        saveOrUpdateDefault("lunchEndTime", "15:00");
        saveOrUpdateDefault("lateArrivalGraceMinutes", "15");
        saveOrUpdateDefault("earlyOutGraceMinutes", "0");
        saveOrUpdateDefault("time_off_policy_mode", "UNITWISE");
        saveOrUpdateDefault("time_off_cycle", "Monthly");
        saveOrUpdateDefault("time_off_short_break_unit_limit", "2");
        saveOrUpdateDefault("time_off_early_out_unit_limit", "2");
        saveOrUpdateDefault("time_off_late_arrival_unit_limit", "2");
        saveOrUpdateDefault("time_off_hourly_limit", "2");
        saveOrUpdateDefault("restricted_holiday_allowance", "2");
    }

    private void saveOrUpdateDefault(String key, String defaultValue) {
        var opt = settingRepository.findById(key);
        if (opt.isEmpty()) {
            settingRepository.save(new Setting(key, defaultValue));
        } else {
            String val = opt.get().getKeyValue();
            if ("shiftStartTime".equals(key) && "09:00".equals(val)) {
                settingRepository.save(new Setting(key, defaultValue));
            } else if ("shiftEndTime".equals(key) && "18:00".equals(val)) {
                settingRepository.save(new Setting(key, defaultValue));
            } else if ("workHoursStart".equals(key) && "09:00".equals(val)) {
                settingRepository.save(new Setting(key, defaultValue));
            } else if ("workHoursEnd".equals(key) && ("17:00".equals(val) || "18:00".equals(val))) {
                settingRepository.save(new Setting(key, defaultValue));
            } else if ("time_off_hourly_limit".equals(key) && "4".equals(val)) {
                settingRepository.save(new Setting(key, defaultValue));
            }
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

    public void updateSettingsBatch(Map<String, String> settings) {
        List<Setting> list = settings.entrySet().stream()
                .map(e -> new Setting(e.getKey(), e.getValue()))
                .toList();
        settingRepository.saveAll(list);
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
