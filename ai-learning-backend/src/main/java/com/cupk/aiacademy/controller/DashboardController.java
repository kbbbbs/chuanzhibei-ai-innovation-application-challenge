package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.mapper.DashboardMapper;
import com.cupk.aiacademy.service.PermissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardMapper dashboardMapper;
    private final PermissionService permissionService;

    public DashboardController(DashboardMapper dashboardMapper, PermissionService permissionService) {
        this.dashboardMapper = dashboardMapper;
        this.permissionService = permissionService;
    }

    @GetMapping("/summary")
    public Result summary() {
        AuthUtils.LoginUser currentUser = permissionService.currentUser();
        Map<String, Object> map = new HashMap<>();

        if ("student".equals(currentUser.getRoleCode())) {
            map.put("pendingHomework", normalizeRows(dashboardMapper.selectPendingHomework(currentUser.getUserId())));
            map.put("upcomingExams", normalizeRows(dashboardMapper.selectUpcomingExams(currentUser.getUserId())));
        } else if ("teacher".equals(currentUser.getRoleCode())) {
            map.put("classCount", dashboardMapper.countTeacherClasses(currentUser.getUserId()));
            map.put("activeClassCount", dashboardMapper.countTeacherActiveClasses(currentUser.getUserId()));
            map.put("pendingGradingCount", dashboardMapper.countPendingGrading(currentUser.getUserId()));
            map.put("lowScoreCount", dashboardMapper.countLowScoreSubmissions(currentUser.getUserId()));
            map.put("recentSubmissions", normalizeRows(dashboardMapper.selectRecentSubmissions(currentUser.getUserId())));
        } else if ("admin".equals(currentUser.getRoleCode())) {
            map.put("classCount", dashboardMapper.countAllClasses());
            map.put("activeClassCount", dashboardMapper.countAllActiveClasses());
            map.put("pendingGradingCount", dashboardMapper.countAllPendingGrading());
            map.put("lowScoreCount", dashboardMapper.countAllLowScoreSubmissions());
            map.put("recentSubmissions", normalizeRows(dashboardMapper.selectAllRecentSubmissions()));
        }

        return Result.success(map);
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        return rows.stream().map(this::normalizeRow).collect(Collectors.toList());
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new HashMap<>();
        row.forEach((key, value) -> normalized.put(normalizeKey(key), value));
        return normalized;
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        return switch (key.toLowerCase()) {
            case "classid" -> "classId";
            case "fullscore" -> "fullScore";
            case "starttime" -> "startTime";
            case "endtime" -> "endTime";
            case "submittime" -> "submitTime";
            case "gradestatus" -> "gradeStatus";
            case "homeworktitle" -> "homeworkTitle";
            case "studentname" -> "studentName";
            default -> key.toLowerCase();
        };
    }
}
