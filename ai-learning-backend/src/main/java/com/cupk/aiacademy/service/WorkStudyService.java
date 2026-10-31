package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cupk.aiacademy.dto.WorkStudyRequest;
import com.cupk.aiacademy.pojo.WorkStudy;

public interface WorkStudyService extends IService<WorkStudy> {
    Page<WorkStudy> pageList(Integer pageNum, Integer pageSize, String jobName, Integer status);
    boolean saveWorkStudy(WorkStudyRequest request);
    boolean updateWorkStudy(Integer id, WorkStudyRequest request);
}
