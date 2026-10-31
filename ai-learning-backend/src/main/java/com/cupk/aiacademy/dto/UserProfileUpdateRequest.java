package com.cupk.aiacademy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    @Size(max = 50)
    private String nickname;

    @Email
    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String phone;

    @Size(max = 255)
    private String avatar;

    @Size(max = 100)
    private String currentPassword;

    @Size(min = 6, max = 100)
    private String newPassword;
}
