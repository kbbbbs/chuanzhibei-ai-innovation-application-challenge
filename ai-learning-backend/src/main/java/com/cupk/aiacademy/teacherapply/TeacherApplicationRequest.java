package com.cupk.aiacademy.teacherapply;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeacherApplicationRequest {
    @NotBlank
    @Size(max = 50)
    private String username;
    @NotBlank
    @Size(max = 100)
    private String password;
    @NotBlank
    @Size(max = 50)
    private String nickname;
    @Email
    @Size(max = 100)
    private String email;
    @Size(max = 20)
    private String phone;
    @NotBlank
    @Size(max = 500)
    private String reason;
}
