package com.example.hr_management_backend.features.settings.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settings")
public class Setting {

    @Id
    private String keyName; // Rename field to avoid SQL reserved keyword 'key' in some databases

    private String keyValue; // Rename to avoid keyword issues or keep it clear

    public Setting() {}

    public Setting(String keyName, String keyValue) {
        this.keyName = keyName;
        this.keyValue = keyValue;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getKeyValue() {
        return keyValue;
    }

    public void setKeyValue(String keyValue) {
        this.keyValue = keyValue;
    }
}
