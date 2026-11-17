package com.cupk.aiacademy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface TeachingClassMapper extends BaseMapper<TeachingClass> {
    @Select("SELECT * FROM t_teaching_class WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    TeachingClass selectForUpdate(Integer id);
}
