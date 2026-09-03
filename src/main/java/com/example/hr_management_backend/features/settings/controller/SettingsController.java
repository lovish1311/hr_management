package com.example.hr_management_backend.features.settings.controller;

import com.example.hr_management_backend.features.settings.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    @Autowired
    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getAllSettings() {
        return ResponseEntity.ok(settingsService.getAllSettings());
    }

    @PutMapping("/{key}")
    public ResponseEntity<Void> updateSetting(@PathVariable String key, @RequestParam String value) {
        settingsService.updateSetting(key, value);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/batch")
    public ResponseEntity<Void> updateSettingsBatch(@RequestBody Map<String, String> body) {
        settingsService.updateSettingsBatch(body);
        return ResponseEntity.ok().build();
    }
}
