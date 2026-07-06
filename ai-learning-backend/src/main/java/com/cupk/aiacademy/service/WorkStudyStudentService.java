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
    Page<WorkStudyStudent> pageList(Integer pageNum, Integer pageSize, Integer workStudyId, Integer studentId, String workMonth, Integer auditStatus);
    boolean saveWorkStudyStudent(WorkStudyStudentRequest request);
    boolean updateWorkStudyStudent(Integer id, WorkStudyStudentRequest request);
    boolean applyForPosition(WorkStudyApplicationRequest request);
    boolean auditApplication(WorkStudyAuditRequest request);
    void exportExcel(Integer workStudyId, Integer studentId, String workMonth, HttpServletResponse response) throws IOException;
    void importExcel(Integer workStudyId, MultipartFile file) throws IOException;
}
