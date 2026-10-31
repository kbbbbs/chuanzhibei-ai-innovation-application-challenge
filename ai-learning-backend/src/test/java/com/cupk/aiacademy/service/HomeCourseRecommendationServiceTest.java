package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.dto.HomeCourseRecommendationVO;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.HomeCourseRecommendationMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.HomeCourseRecommendation;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeCourseRecommendationServiceTest {
    @Mock private HomeCourseRecommendationMapper recommendationMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private TeachingClassMapper teachingClassMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private PermissionService permissionService;

    @Test
    void joinsPublishedCourseAndReturnsLearningTarget() {
        HomeCourseRecommendation recommendation = new HomeCourseRecommendation();
        recommendation.setId(1);
        recommendation.setCourseId(33);
        recommendation.setStatus(1);

        Course course = new Course();
        course.setId(33);
        course.setTitle("LeetCode刷题精讲");
        course.setStatus(1);

        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(46);
        teachingClass.setCourseId(33);
        teachingClass.setClassName("LeetCode公共学习班");
        teachingClass.setStatus(1);

        AuthUtils.LoginUser student = new AuthUtils.LoginUser();
        student.setUserId(2001);
        student.setRoleCode("student");

        when(permissionService.currentUser()).thenReturn(student);
        when(recommendationMapper.selectOne(any(QueryWrapper.class))).thenReturn(recommendation);
        when(courseMapper.selectById(33)).thenReturn(course);
        when(teachingClassMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(teachingClass));
        when(classStudentMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        HomeCourseRecommendationService service = new HomeCourseRecommendationService(
                recommendationMapper, courseMapper, teachingClassMapper, classStudentMapper, permissionService);

        HomeCourseRecommendationVO result = service.joinPublicCourse(33);

        ArgumentCaptor<ClassStudent> membership = ArgumentCaptor.forClass(ClassStudent.class);
        verify(classStudentMapper).insert(membership.capture());
        assertEquals(46, membership.getValue().getClassId());
        assertEquals(2001, membership.getValue().getStudentId());
        assertEquals(1, membership.getValue().getStatus());
        assertEquals(46, result.getClassId());
        assertTrue(result.getEnrolled());
        assertEquals("/course-learn/33?classId=46", result.getTargetUrl());
    }
}
