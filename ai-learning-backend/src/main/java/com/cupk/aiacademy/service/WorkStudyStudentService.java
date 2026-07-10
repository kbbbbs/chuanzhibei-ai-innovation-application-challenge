package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cupk.aiacademy.dto.WorkStudyApplicationRequest;
import com.cupk.aiacademy.dto.WorkStudyAuditRequest;
import com.cupk.aiacademy.dto.WorkStudyStudentRequest;
import com.cupk.aiacademy.pojo.WorkStudyStudent;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface WorkStudyStudentService extends IService<WorkStudyStudent> {
    default Page<WorkStudyStudent> pageList(Integer pageNum, Integer pageSize, Integer workStudyId, Integer studentId, String workMonth, Integer auditStatus) {
        return pageList(pageNum, pageSize, workStudyId, studentId, workMonth, auditStatus, null);
    }
    Page<WorkStudyStudent> pageList(Integer pageNum, Integer pageSize, Integer workStudyId, Integer studentId, String workMonth, Integer auditStatus, String keyword);
    boolean saveWorkStudyStudent(WorkStudyStudentRequest request);
    boolean updateWorkStudyStudent(Integer id, WorkStudyStudentRequest request);
    boolean applyForPosition(WorkStudyApplicationRequest request);
    boolean auditApplication(WorkStudyAuditRequest request);
    default void exportExcel(Integer workStudyId, Integer studentId, String workMonth, String format, HttpServletResponse response) throws IOException {
        exportExcel(workStudyId, studentId, workMonth, format, null, response);
    }
    void exportExcel(Integer workStudyId, Integer studentId, String workMonth, String format, String keyword, HttpServletResponse response) throws IOException;
    void importExcel(Integer workStudyId, MultipartFile file) throws IOException;
}
