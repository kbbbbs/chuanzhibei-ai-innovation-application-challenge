package com.cupk.aiacademy.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cupk.aiacademy.dto.WorkStudyStudentImportDTO;
import com.cupk.aiacademy.dto.WorkStudyStudentRequest;
import com.cupk.aiacademy.mapper.WorkStudyStudentMapper;
import com.cupk.aiacademy.pojo.WorkStudyStudent;
import com.cupk.aiacademy.service.WorkStudyStudentService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        calculateTotalAmount(wss);
        return this.updateById(wss);
    }

    private void calculateTotalAmount(WorkStudyStudent wss) {
        if (wss.getWorkHours() != null && wss.getHourlyRate() != null) {
            wss.setTotalAmount(wss.getWorkHours().multiply(wss.getHourlyRate()));
        }
    }

    @Override
    public void exportExcel(Integer workStudyId, Integer studentId, String workMonth, HttpServletResponse response) throws IOException {
        List<WorkStudyStudent> list = this.baseMapper.selectListForExport(workStudyId, studentId, workMonth);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("劳务费名单", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), WorkStudyStudent.class).sheet("劳务费名单").doWrite(list);
    }

    @Override
    public void importExcel(Integer workStudyId, MultipartFile file) throws IOException {
        List<WorkStudyStudent> list = new ArrayList<>();
        EasyExcel.read(file.getInputStream(), WorkStudyStudentImportDTO.class, new ReadListener<WorkStudyStudentImportDTO>() {
            @Override
            public void invoke(WorkStudyStudentImportDTO dto, AnalysisContext context) {
                WorkStudyStudent wss = new WorkStudyStudent();
                wss.setWorkStudyId(workStudyId);
                wss.setStudentId(dto.getStudentId());
                wss.setWorkMonth(dto.getWorkMonth());
                wss.setWorkHours(dto.getWorkHours());
                wss.setHourlyRate(dto.getHourlyRate());
                calculateTotalAmount(wss);
                wss.setStatus(0);
                list.add(wss);
            }
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();
        if (!list.isEmpty()) {
            this.saveBatch(list);
        }
    }
}
