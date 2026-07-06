package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.WorkStudyRequest;
import com.cupk.aiacademy.dto.WorkStudyStudentRequest;
import com.cupk.aiacademy.pojo.WorkStudy;
import com.cupk.aiacademy.pojo.WorkStudyStudent;
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
        return workStudyService.saveWorkStudy(request) ? Result.success() : Result.error();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @Valid @RequestBody WorkStudyRequest request) {
        return workStudyService.updateWorkStudy(id, request) ? Result.success() : Result.error("404", "岗位不存在");
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        return workStudyService.removeById(id) ? Result.success() : Result.error("404", "岗位不存在");
    }

    @GetMapping("/students")
    public Result studentPageList(@RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                  @RequestParam(required = false) Integer workStudyId,
                                  @RequestParam(required = false) Integer studentId,
                                  @RequestParam(required = false) String workMonth,
                                  @RequestParam(required = false) Integer auditStatus) {
        pageNum = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        pageSize = pageSize == null || pageSize <= 0 ? 10 : pageSize;
        return Result.success(workStudyStudentService.pageList(pageNum, pageSize, workStudyId, studentId, workMonth, auditStatus));
    }

    @GetMapping("/students/{id}")
    public Result getStudentById(@PathVariable Integer id) {
        WorkStudyStudent wss = workStudyStudentService.getById(id);
        return wss == null ? Result.error("404", "记录不存在") : Result.success(wss);
    }

    @PostMapping("/students")
    public Result saveStudent(@Valid @RequestBody WorkStudyStudentRequest request) {
        return workStudyStudentService.saveWorkStudyStudent(request) ? Result.success() : Result.error();
    }

    @PutMapping("/students/{id}")
    public Result updateStudent(@PathVariable Integer id, @Valid @RequestBody WorkStudyStudentRequest request) {
        return workStudyStudentService.updateWorkStudyStudent(id, request) ? Result.success() : Result.error("404", "记录不存在");
    }

    @DeleteMapping("/students/{id}")
    public Result deleteStudent(@PathVariable Integer id) {
        return workStudyStudentService.removeById(id) ? Result.success() : Result.error("404", "记录不存在");
    }

    @PostMapping("/students/import")
    public Result importExcel(@RequestParam Integer workStudyId, @RequestParam MultipartFile file) {
        try {
            workStudyStudentService.importExcel(workStudyId, file);
            return Result.success();
        } catch (IOException e) {
            return Result.error("500", "导入失败：" + e.getMessage());
        }
    }

    @GetMapping("/students/export")
    public void exportExcel(@RequestParam(required = false) Integer workStudyId,
                            @RequestParam(required = false) Integer studentId,
                            @RequestParam(required = false) String workMonth,
                            HttpServletResponse response) {
        try {
            workStudyStudentService.exportExcel(workStudyId, studentId, workMonth, response);
        } catch (IOException e) {
            response.setStatus(500);
        }
    }
}
