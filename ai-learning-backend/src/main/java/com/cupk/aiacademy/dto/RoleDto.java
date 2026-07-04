package com.cupk.aiacademy.dto;

import com.cupk.aiacademy.pojo.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleDto {
    @NotBlank
    @Size(max = 50)
    private String roleName;

    @NotBlank
    @Size(max = 50)
    private String roleCode;

    public Role toEntity() {
        Role role = new Role();
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        return role;
    }
}
