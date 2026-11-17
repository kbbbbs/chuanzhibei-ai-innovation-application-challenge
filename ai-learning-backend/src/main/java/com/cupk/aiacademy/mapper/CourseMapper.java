package com.cupk.aiacademy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cupk.aiacademy.pojo.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    @Select("SELECT * FROM t_course WHERE id=#{id} FOR UPDATE")
    @org.apache.ibatis.annotations.Options(useCache = false, flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    Course selectForUpdate(Integer id);

    @Select("SELECT DISTINCT c.* FROM t_course c " +
            "JOIN t_teaching_class tc ON tc.course_id = c.id " +
            "JOIN t_class_student cs ON cs.class_id = tc.id " +
            "WHERE cs.student_id = #{studentId} AND cs.status = 1 AND tc.status = 1 AND tc.course_open = TRUE AND c.status = 1")
    List<Course> selectCoursesByStudentId(Integer studentId);

    @Select("SELECT DISTINCT * FROM t_course WHERE teacher_id = #{teacherId}")
    List<Course> selectCoursesByTeacherId(Integer teacherId);
}
