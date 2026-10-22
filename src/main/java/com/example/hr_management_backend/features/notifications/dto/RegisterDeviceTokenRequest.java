package com.example.hr_management_backend.features.notifications.dto;

import com.example.hr_management_backend.features.notifications.model.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterDeviceTokenRequest {

    @NotBlank(message = "FCM registration token cannot be blank")
    private String token;

    @NotNull(message = "Device type must be specified (ANDROID, WEB, IOS)")
    private DeviceType deviceType;

    private String deviceName;
}
