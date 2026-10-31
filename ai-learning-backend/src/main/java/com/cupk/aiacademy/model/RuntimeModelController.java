package com.cupk.aiacademy.model;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/models")
public class RuntimeModelController {
    private final RuntimeModelCapabilityService service;
    public RuntimeModelController(RuntimeModelCapabilityService service) { this.service=service; }
    public static void requireAdmin() {
        if (UserContext.getCurrentUserId()==null || !"admin".equals(UserContext.getCurrentRoleCode()))
            throw new ForbiddenException("仅管理员可查看运行模型或执行能力检查");
    }
    @GetMapping("/status") public Result status() { requireAdmin(); return Result.success(service.status()); }
    @PostMapping("/{role}/probe") public Result probe(@PathVariable String role) {
        requireAdmin();
        if(!java.util.List.of("generation","planning","embedding","ocr").contains(role)) throw new BusinessException("未知模型能力");
        return Result.success(service.probe(role));
    }
}
