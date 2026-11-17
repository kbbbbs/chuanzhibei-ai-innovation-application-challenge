package com.cupk.aiacademy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClassStudentMapper extends BaseMapper<ClassStudent> {
    @Select("SELECT * FROM t_class_student WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    ClassStudent selectForUpdate(Integer id);
}
