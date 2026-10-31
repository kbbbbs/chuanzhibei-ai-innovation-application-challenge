package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.WorkStudyApplicationRequest;
import com.cupk.aiacademy.dto.WorkStudyAuditRequest;
import com.cupk.aiacademy.dto.WorkStudyRequest;
import com.cupk.aiacademy.dto.WorkStudyStudentRequest;
import com.cupk.aiacademy.pojo.WorkStudy;
import com.cupk.aiacademy.pojo.WorkStudyStudent;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.service.WorkStudyService;
import com.cupk.aiacademy.service.WorkStudyStudentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/work-studies")
public class WorkStudyController {

    @Autowired
    private WorkStudyService workStudyService;

    @Autowired
    private WorkStudyStudentService workStudyStudentService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result pageList(@RequestParam(defaultValue = "1") Integer pageNum,
                           @RequestParam(defaultValue = "10") Integer pageSize,
                           @RequestParam(required = false) String jobName,
                           @RequestParam(required = false) Integer status) {
        pageNum = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        pageSize = pageSize == null || pageSize <= 0 ? 10 : pageSize;
        return Result.success(workStudyService.pageList(pageNum, pageSize, jobName, status));
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Integer id) {
        WorkStudy workStudy = workStudyService.getById(id);
        return workStudy == null ? Result.error("404", "岗位不存在") : Result.success(workStudy);
    }

    @PostMapping
    public Result save(@Valid @RequestBody WorkStudyRequest request) {
        permissionService.requireTeacherOrAdmin();
        return workStudyService.saveWorkStudy(request) ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody WorkStudyRequest request) {
        permissionService.requireTeacherOrAdmin();
        return workStudyService.updateWorkStudy(id, request) ? Result.success() : Result.error("404", "岗位不存在");
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        permissionService.requireTeacherOrAdmin();
        return workStudyService.removeById(id) ? Result.success() : Result.error("404", "岗位不存在");
    }

    @PostMapping("/apply")
    public Result applyForPosition(@Valid @RequestBody WorkStudyApplicationRequest request) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (permissionService.isStudent(user) && !user.getUserId().equals(request.getStudentId())) {
            return Result.error("403", "只能申请自己的岗位");
        }
        return workStudyStudentService.applyForPosition(request)
                ? Result.success()
                : Result.error("409", "已申请该岗位，请勿重复申请");
    }

    @PutMapping("/audit")
    public Result auditApplication(@Valid @RequestBody WorkStudyAuditRequest request) {
        permissionService.requireTeacherOrAdmin();
        return workStudyStudentService.auditApplication(request)
                ? Result.success()
                : Result.error("404", "申请记录不存在");
    }

    @GetMapping("/my-applications")
    public Result myApplications(@RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                 @RequestParam(required = false) Integer studentId,
                                 @RequestParam(required = false) Integer auditStatus) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (permissionService.isStudent(user)) {
            studentId = user.getUserId();
        }
        permissionService.requireSelfStudent(studentId);
        pageNum = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        pageSize = pageSize == null || pageSize <= 0 ? 10 : pageSize;
        return Result.success(workStudyStudentService.pageList(pageNum, pageSize, null, studentId, null, auditStatus));
    }

    @GetMapping("/pending-applications")
    public Result pendingApplications(@RequestParam(defaultValue = "1") Integer pageNum,
                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                      @RequestParam(required = false) Integer workStudyId,
                                      @RequestParam(required = false) Integer studentId) {
        permissionService.requireTeacherOrAdmin();
        pageNum = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        pageSize = pageSize == null || pageSize <= 0 ? 10 : pageSize;
        return Result.success(workStudyStudentService.pageList(pageNum, pageSize, workStudyId, studentId, null, 0));
    }

    @GetMapping("/students")
    public Result studentPageList(@RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                  @RequestParam(required = false) Integer workStudyId,
                                  @RequestParam(required = false) Integer studentId,
                                  @RequestParam(required = false) String workMonth,
                                  @RequestParam(required = false) Integer auditStatus,
                                  @RequestParam(required = false) String keyword) {
        pageNum = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        pageSize = pageSize == null || pageSize <= 0 ? 10 : pageSize;
        return Result.success(workStudyStudentService.pageList(pageNum, pageSize, workStudyId, studentId, workMonth, auditStatus, keyword));
    }

    @GetMapping("/students/{id}")
    public Result getStudentById(@PathVariable Integer id) {
        WorkStudyStudent wss = workStudyStudentService.getById(id);
        return wss == null ? Result.error("404", "记录不存在") : Result.success(wss);
    }

    @PostMapping("/students")
    public Result saveStudent(@Valid @RequestBody WorkStudyStudentRequest request) {
        permissionService.requireTeacherOrAdmin();
        return workStudyStudentService.saveWorkStudyStudent(request) ? Result.success() : Result.error();
    }

    @PutMapping("/students/{id}")
    public Result updateStudent(@PathVariable Integer id, @Valid @RequestBody WorkStudyStudentRequest request) {
        permissionService.requireTeacherOrAdmin();
        return workStudyStudentService.updateWorkStudyStudent(id, request) ? Result.success() : Result.error("404", "记录不存在");
    }

    @DeleteMapping("/students/{id}")
    public Result deleteStudent(@PathVariable Integer id) {
        permissionService.requireTeacherOrAdmin();
        return workStudyStudentService.removeById(id) ? Result.success() : Result.error("404", "记录不存在");
    }

    @PostMapping("/students/import")
    public Result importExcel(@RequestParam Integer workStudyId, @RequestParam MultipartFile file) {
        permissionService.requireTeacherOrAdmin();
        try {
            workStudyStudentService.importExcel(workStudyId, file);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error("400", "导入失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.error("500", "导入失败：" + e.getMessage());
        }
    }

    @GetMapping("/students/export")
    public void exportExcel(@RequestParam(required = false) Integer workStudyId,
                             @RequestParam(required = false) Integer studentId,
                             @RequestParam(required = false) String workMonth,
                             @RequestParam(defaultValue = "xlsx") String format,
                             @RequestParam(required = false) String keyword,
                             HttpServletResponse response) {
        permissionService.requireTeacherOrAdmin();
        try {
            workStudyStudentService.exportExcel(workStudyId, studentId, workMonth, format, keyword, response);
        } catch (IOException e) {
            response.setStatus(500);
        }
    }
}
