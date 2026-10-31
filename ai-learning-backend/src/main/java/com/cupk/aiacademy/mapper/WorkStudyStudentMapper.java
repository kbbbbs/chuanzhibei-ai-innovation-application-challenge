package com.cupk.aiacademy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.pojo.WorkStudyStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkStudyStudentMapper extends BaseMapper<WorkStudyStudent> {
    Page<WorkStudyStudent> selectPageWithJoin(Page<WorkStudyStudent> page,
                                               @Param("workStudyId") Integer workStudyId,
                                               @Param("studentId") Integer studentId,
                                               @Param("workMonth") String workMonth,
                                               @Param("auditStatus") Integer auditStatus,
                                               @Param("keyword") String keyword);

    List<WorkStudyStudent> selectListForExport(@Param("workStudyId") Integer workStudyId,
                                                @Param("studentId") Integer studentId,
                                                @Param("workMonth") String workMonth,
                                                @Param("keyword") String keyword);
}
