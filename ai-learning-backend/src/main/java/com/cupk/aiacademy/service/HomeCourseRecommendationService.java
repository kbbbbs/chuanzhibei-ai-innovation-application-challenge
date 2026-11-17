package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.HomeCourseRecommendationRequest;
import com.cupk.aiacademy.dto.HomeCourseRecommendationVO;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.HomeCourseRecommendationMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.HomeCourseRecommendation;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HomeCourseRecommendationService {
    private final HomeCourseRecommendationMapper recommendationMapper;
    private final CourseMapper courseMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ClassStudentMapper classStudentMapper;
    private final PermissionService permissionService;

    public HomeCourseRecommendationService(HomeCourseRecommendationMapper recommendationMapper,
                                           CourseMapper courseMapper,
                                           TeachingClassMapper teachingClassMapper,
                                           ClassStudentMapper classStudentMapper,
                                           PermissionService permissionService) {
        this.recommendationMapper = recommendationMapper;
        this.courseMapper = courseMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.classStudentMapper = classStudentMapper;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<HomeCourseRecommendationVO> listPublished() {
        QueryWrapper<HomeCourseRecommendation> query = new QueryWrapper<>();
        query.eq("status", 1).orderByAsc("sort_order").orderByDesc("update_time").orderByAsc("id");
        List<HomeCourseRecommendationVO> items = toViews(recommendationMapper.selectList(query), true);
        if (!"student".equals(UserContext.getCurrentRoleCode())) {
            return items;
        }

        Map<Integer, TeachingClass> studentClasses = studentClassesByCourse(UserContext.getCurrentUserId());
        items.forEach(item -> {
            TeachingClass enrolledClass = studentClasses.get(item.getCourseId());
            item.setJoinable(false);
            item.setEnrolled(enrolledClass != null);
            if (enrolledClass != null) {
                applyStudentClass(item, enrolledClass);
            }
        });
        return items;
    }

    @Transactional
    public HomeCourseRecommendationVO joinPublicCourse(Integer courseId) {
        permissionService.requireStudent();
        Integer studentId = permissionService.currentUser().getUserId();

        HomeCourseRecommendation recommendation = recommendationMapper.selectOne(new QueryWrapper<HomeCourseRecommendation>()
                .eq("course_id", courseId)
                .eq("status", 1)
                .last("LIMIT 1"));
        Course course = courseMapper.selectById(courseId);
        if (recommendation == null || course == null || !Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException("该课程不是可加入的首页公共课");
        }

        TeachingClass teachingClass = studentClassesByCourse(studentId).get(courseId);
        if (teachingClass == null) {
            throw new com.cupk.aiacademy.common.ForbiddenException("请联系管理员加入已开放的教学班");
        }

        HomeCourseRecommendationVO view = toView(recommendation, course);
        view.setEnrolled(true);
        view.setJoinable(false);
        applyStudentClass(view, teachingClass);
        return view;
    }

    @Transactional(readOnly = true)
    public List<HomeCourseRecommendationVO> listForAdmin() {
        QueryWrapper<HomeCourseRecommendation> query = new QueryWrapper<>();
        query.orderByAsc("sort_order").orderByDesc("update_time").orderByAsc("id");
        return toViews(recommendationMapper.selectList(query), false);
    }

    @Transactional
    public HomeCourseRecommendationVO create(HomeCourseRecommendationRequest request) {
        validate(request, null);
        HomeCourseRecommendation item = new HomeCourseRecommendation();
        apply(item, request);
        LocalDateTime now = LocalDateTime.now();
        item.setCreateTime(now);
        item.setUpdateTime(now);
        recommendationMapper.insert(item);
        return toView(item, courseMapper.selectById(item.getCourseId()));
    }

    @Transactional
    public HomeCourseRecommendationVO update(Integer id, HomeCourseRecommendationRequest request) {
        HomeCourseRecommendation item = requireItem(id);
        validate(request, id);
        apply(item, request);
        item.setUpdateTime(LocalDateTime.now());
        recommendationMapper.updateById(item);
        return toView(item, courseMapper.selectById(item.getCourseId()));
    }

    @Transactional
    public void delete(Integer id) {
        requireItem(id);
        recommendationMapper.deleteById(id);
    }

    private void validate(HomeCourseRecommendationRequest request, Integer currentId) {
        if (request == null || request.getCourseId() == null) {
            throw new BusinessException("请选择课程");
        }
        if (courseMapper.selectById(request.getCourseId()) == null) {
            throw new BusinessException("课程不存在");
        }
        if (request.getStatus() == null || request.getStatus() == 1) {
            QueryWrapper<TeachingClass> publicClass = new QueryWrapper<>();
            publicClass.eq("course_id", request.getCourseId()).eq("status", 1);
            if (teachingClassMapper.selectCount(publicClass) == 0) {
                throw new BusinessException("首页公共课必须至少存在一个启用的教学班");
            }
        }
        QueryWrapper<HomeCourseRecommendation> duplicate = new QueryWrapper<>();
        duplicate.eq("course_id", request.getCourseId());
        if (currentId != null) duplicate.ne("id", currentId);
        if (recommendationMapper.selectCount(duplicate) > 0) {
            throw new BusinessException("该课程已在首页推荐列表中");
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            throw new BusinessException("状态只能为启用或停用");
        }
    }

    private void apply(HomeCourseRecommendation item, HomeCourseRecommendationRequest request) {
        item.setCourseId(request.getCourseId());
        item.setReason(trimToNull(request.getReason()));
        item.setSortOrder(request.getSortOrder() == null ? 1 : request.getSortOrder());
        item.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private HomeCourseRecommendation requireItem(Integer id) {
        HomeCourseRecommendation item = recommendationMapper.selectById(id);
        if (item == null) throw new BusinessException("首页推荐记录不存在");
        return item;
    }

    private List<HomeCourseRecommendationVO> toViews(List<HomeCourseRecommendation> items, boolean publishedOnly) {
        if (items.isEmpty()) return List.of();
        Map<Integer, Course> courses = courseMapper.selectBatchIds(items.stream()
                        .map(HomeCourseRecommendation::getCourseId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Course::getId, Function.identity()));
        return items.stream()
                .filter(item -> courses.containsKey(item.getCourseId()))
                .filter(item -> !publishedOnly || Integer.valueOf(1).equals(courses.get(item.getCourseId()).getStatus()))
                .map(item -> toView(item, courses.get(item.getCourseId())))
                .toList();
    }

    private HomeCourseRecommendationVO toView(HomeCourseRecommendation item, Course course) {
        HomeCourseRecommendationVO view = new HomeCourseRecommendationVO();
        view.setId(item.getId());
        view.setCourseId(item.getCourseId());
        view.setReason(item.getReason());
        view.setSortOrder(item.getSortOrder());
        view.setStatus(item.getStatus());
        view.setUpdateTime(item.getUpdateTime());
        view.setTargetUrl("/courses/" + item.getCourseId());
        if (course != null) {
            view.setCourseTitle(course.getTitle());
            view.setCourseDescription(course.getDescription());
            view.setCoverUrl(course.getCoverUrl() == null ? course.getCover() : course.getCoverUrl());
            view.setTeacher(course.getTeacher());
            view.setDifficulty(course.getDifficulty());
            view.setCourseStatus(course.getStatus());
        }
        return view;
    }

    private Map<Integer, TeachingClass> studentClassesByCourse(Integer studentId) {
        List<Integer> classIds = permissionService.studentLearningClassIds(studentId);
        if (classIds.isEmpty()) return Map.of();
        QueryWrapper<TeachingClass> query = new QueryWrapper<>();
        query.in("id", classIds).eq("status", 1).eq("course_open", true).orderByDesc("id");
        return teachingClassMapper.selectList(query).stream()
                .filter(item -> item.getCourseId() != null && Boolean.TRUE.equals(item.getCourseOpen()))
                .collect(Collectors.toMap(TeachingClass::getCourseId, Function.identity(), (first, ignored) -> first));
    }

    private void applyStudentClass(HomeCourseRecommendationVO view, TeachingClass teachingClass) {
        view.setClassId(teachingClass.getId());
        view.setClassName(teachingClass.getClassName());
        view.setTargetUrl("/course-learn/" + view.getCourseId() + "?classId=" + teachingClass.getId());
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
