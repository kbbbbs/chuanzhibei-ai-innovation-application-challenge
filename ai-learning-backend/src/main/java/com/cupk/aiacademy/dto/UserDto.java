package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserDto {
    @NotBlank
    @Size(max = 50)
    private String username;

    @Size(max = 100)
    private String password;

    @Size(max = 50)
    private String nickname;

    @Email
    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String phone;

    @Size(max = 255)
    private String avatar;

    @Min(0)
    @Max(1)
    private Integer status;

    @Positive
    private Integer roleId;

    public User toEntity() {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        user.setAvatar(avatar);
        user.setStatus(status);
        user.setRoleId(roleId);
        return user;
    }
}
