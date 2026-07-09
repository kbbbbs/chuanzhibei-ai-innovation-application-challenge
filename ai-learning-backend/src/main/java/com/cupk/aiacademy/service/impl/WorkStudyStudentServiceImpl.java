package com.cupk.aiacademy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.WorkStudyApplicationRequest;
import com.cupk.aiacademy.dto.WorkStudyAuditRequest;
import com.cupk.aiacademy.dto.WorkStudyStudentImportDTO;
import com.cupk.aiacademy.dto.WorkStudyStudentRequest;
import com.cupk.aiacademy.dto.imports.WorkStudyStudentExportRow;
import com.cupk.aiacademy.mapper.WorkStudyStudentMapper;
import com.cupk.aiacademy.pojo.WorkStudyStudent;
import com.cupk.aiacademy.service.WorkStudyStudentService;
import com.cupk.aiacademy.service.imports.TabularFileSupport;
import com.cupk.aiacademy.service.imports.TabularFileSupport.ExportFile;
import com.cupk.aiacademy.service.imports.TabularFileSupport.TabularRow;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkStudyStudentServiceImpl extends ServiceImpl<WorkStudyStudentMapper, WorkStudyStudent> implements WorkStudyStudentService {

    @Override
    public Page<WorkStudyStudent> pageList(Integer pageNum, Integer pageSize, Integer workStudyId, Integer studentId, String workMonth, Integer auditStatus) {
        Page<WorkStudyStudent> page = new Page<>(pageNum, pageSize);
        return this.baseMapper.selectPageWithJoin(page, workStudyId, studentId, workMonth, auditStatus);
    }

    @Override
    public boolean saveWorkStudyStudent(WorkStudyStudentRequest request) {
        WorkStudyStudent wss = new WorkStudyStudent();
        BeanUtils.copyProperties(request, wss);
        LocalDateTime now = LocalDateTime.now();
        wss.setCreateTime(now);
        wss.setUpdateTime(now);
        calculateTotalAmount(wss);
        return this.save(wss);
    }

    @Override
    public boolean updateWorkStudyStudent(Integer id, WorkStudyStudentRequest request) {
        WorkStudyStudent wss = this.getById(id);
        if (wss == null) {
            return false;
        }
        BeanUtils.copyProperties(request, wss);
        wss.setUpdateTime(LocalDateTime.now());
        calculateTotalAmount(wss);
        return this.updateById(wss);
    }

    @Override
    public boolean applyForPosition(WorkStudyApplicationRequest request) {
        QueryWrapper<WorkStudyStudent> duplicateQuery = new QueryWrapper<>();
        duplicateQuery.eq("work_study_id", request.getWorkStudyId())
                .eq("student_id", request.getStudentId());
        if (this.count(duplicateQuery) > 0) {
            return false;
        }

        WorkStudyStudent application = new WorkStudyStudent();
        application.setWorkStudyId(request.getWorkStudyId());
        application.setStudentId(request.getStudentId());
        application.setStatus(1);
        application.setAuditStatus(0);
        LocalDateTime now = LocalDateTime.now();
        application.setApplyTime(now);
        application.setCreateTime(now);
        application.setUpdateTime(now);
        return this.save(application);
    }

    @Override
    public boolean auditApplication(WorkStudyAuditRequest request) {
        WorkStudyStudent application = this.getById(request.getApplicationId());
        if (application == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        application.setAuditStatus(request.getAuditStatus());
        application.setAuditorId(request.getAuditorId());
        application.setAuditTime(now);
        application.setRejectReason(request.getAuditStatus() != null && request.getAuditStatus() == 2 ? request.getRejectReason() : null);
        application.setUpdateTime(now);
        return this.updateById(application);
    }

    private void calculateTotalAmount(WorkStudyStudent wss) {
        if (wss.getWorkHours() != null && wss.getHourlyRate() != null) {
            wss.setTotalAmount(wss.getWorkHours().multiply(wss.getHourlyRate()));
        }
    }

    @Override
    public void exportExcel(Integer workStudyId, Integer studentId, String workMonth, String format, HttpServletResponse response) throws IOException {
        List<WorkStudyStudent> list = this.baseMapper.selectListForExport(workStudyId, studentId, workMonth);
        List<WorkStudyStudentExportRow> rows = list.stream().map(this::toExportRow).toList();
        ExportFile file = TabularFileSupport.exportFile("劳务费名单", format, WorkStudyStudentExportRow.class, rows);
        response.setContentType(file.contentType());
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName);
        response.getOutputStream().write(file.bytes());
    }

    @Override
    public void importExcel(Integer workStudyId, MultipartFile file) throws IOException {
        List<WorkStudyStudent> list = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (TabularRow<WorkStudyStudentImportDTO> row : TabularFileSupport.read(file, WorkStudyStudentImportDTO.class)) {
            WorkStudyStudentImportDTO dto = row.data();
            List<String> rowErrors = new ArrayList<>();
            WorkStudyStudent wss = new WorkStudyStudent();
            wss.setWorkStudyId(workStudyId);
            wss.setStudentId(parseInteger(dto.getStudentId(), "学生编号", rowErrors));
            wss.setWorkMonth(requiredText(dto.getWorkMonth(), "工作月份", rowErrors));
            wss.setWorkHours(parseDecimal(dto.getWorkHours(), "工作时长", rowErrors));
            wss.setHourlyRate(parseDecimal(dto.getHourlyRate(), "适用时薪", rowErrors));
            validateWorkStudyStudentImport(wss, rowErrors);
            if (rowErrors.isEmpty()) {
                calculateTotalAmount(wss);
                LocalDateTime now = LocalDateTime.now();
                wss.setStatus(0);
                wss.setCreateTime(now);
                wss.setUpdateTime(now);
                list.add(wss);
            } else {
                errors.add("第" + row.rowNumber() + "行：" + String.join("，", rowErrors));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("；", errors));
        }
        if (!list.isEmpty()) {
            this.saveBatch(list);
        }
    }

    private WorkStudyStudentExportRow toExportRow(WorkStudyStudent item) {
        WorkStudyStudentExportRow row = new WorkStudyStudentExportRow();
        row.setWorkStudyId(toText(item.getWorkStudyId()));
        row.setJobName(item.getJobName());
        row.setStudentId(toText(item.getStudentId()));
        row.setStudentName(item.getStudentName());
        row.setWorkMonth(item.getWorkMonth());
        row.setWorkHours(toText(item.getWorkHours()));
        row.setHourlyRate(toText(item.getHourlyRate()));
        row.setTotalAmount(toText(item.getTotalAmount()));
        row.setStatus(item.getStatus() != null && item.getStatus() == 1 ? "正常" : "停用");
        return row;
    }

    private void validateWorkStudyStudentImport(WorkStudyStudent item, List<String> errors) {
        if (item.getStudentId() != null && item.getStudentId() <= 0) {
            errors.add("学生编号必须大于0");
        }
        if (item.getWorkMonth() != null && !item.getWorkMonth().matches("\\d{4}-(0[1-9]|1[0-2])")) {
            errors.add("工作月份格式应为yyyy-MM");
        }
        if (item.getWorkHours() != null && item.getWorkHours().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("工作时长不能小于0");
        }
        if (item.getHourlyRate() != null && item.getHourlyRate().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("适用时薪不能小于0");
        }
    }

    private Integer parseInteger(String raw, String fieldName, List<String> errors) {
        String value = requiredText(raw, fieldName, errors);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            errors.add(fieldName + "必须是整数");
            return null;
        }
    }

    private BigDecimal parseDecimal(String raw, String fieldName, List<String> errors) {
        String value = requiredText(raw, fieldName, errors);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            errors.add(fieldName + "必须是数字");
            return null;
        }
    }

    private String requiredText(String raw, String fieldName, List<String> errors) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            errors.add(fieldName + "不能为空");
            return null;
        }
        return value;
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }
}
