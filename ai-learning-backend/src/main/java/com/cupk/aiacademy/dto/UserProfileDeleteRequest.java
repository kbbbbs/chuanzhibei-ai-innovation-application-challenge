package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileDeleteRequest {
    @NotBlank
    @Size(max = 100)
    private String currentPassword;
}
