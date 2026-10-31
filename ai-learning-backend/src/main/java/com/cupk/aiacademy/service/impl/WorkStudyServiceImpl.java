package com.cupk.aiacademy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cupk.aiacademy.dto.WorkStudyRequest;
import com.cupk.aiacademy.mapper.WorkStudyMapper;
import com.cupk.aiacademy.pojo.WorkStudy;
import com.cupk.aiacademy.service.WorkStudyService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkStudyServiceImpl extends ServiceImpl<WorkStudyMapper, WorkStudy> implements WorkStudyService {

    @Override
    public Page<WorkStudy> pageList(Integer pageNum, Integer pageSize, String jobName, Integer status) {
        Page<WorkStudy> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WorkStudy> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(jobName)) {
            wrapper.like(WorkStudy::getJobName, jobName);
        }
        if (status != null) {
            wrapper.eq(WorkStudy::getStatus, status);
        }
        wrapper.orderByDesc(WorkStudy::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    public boolean saveWorkStudy(WorkStudyRequest request) {
        WorkStudy workStudy = new WorkStudy();
        BeanUtils.copyProperties(request, workStudy);
        return this.save(workStudy);
    }

    @Override
    public boolean updateWorkStudy(Integer id, WorkStudyRequest request) {
        WorkStudy workStudy = this.getById(id);
        if (workStudy == null) {
            return false;
        }
        BeanUtils.copyProperties(request, workStudy);
        return this.updateById(workStudy);
    }
}
