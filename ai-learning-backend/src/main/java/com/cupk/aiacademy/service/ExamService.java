package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.common.PaperStatus;
import com.cupk.aiacademy.dto.ExamRequestDTO;
import com.cupk.aiacademy.dto.ExamResponseDTO;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.pojo.ExamPaper;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExamService {
    private final ExamSessionMapper examSessionMapper;
    private final PaperService paperService;
    private final PermissionService permissionService;

    public ExamService(ExamSessionMapper examSessionMapper,
                       PaperService paperService,
                       PermissionService permissionService) {
        this.examSessionMapper = examSessionMapper;
        this.paperService = paperService;
        this.permissionService = permissionService;
    }

    @Transactional
    public ExamResponseDTO create(ExamRequestDTO request) {
        validateRequest(request);
        prepareForWrite(request);
        ExamPaper paper = paperService.getEntityById(request.getPaperId());
        requirePublishedPaper(paper, "只能使用已发布的试卷创建考试，当前试卷状态：");
        ExamSession exam = new ExamSession();
        fillExam(exam, request);
        LocalDateTime now = LocalDateTime.now();
        exam.setCreateTime(now);
        exam.setUpdateTime(now);
        examSessionMapper.insert(exam);
        return toResponse(exam);
    }

    @Transactional
    public ExamResponseDTO update(Long id, ExamRequestDTO request) {
        validateRequest(request);
        ExamSession exam = getEntityById(id);
        permissionService.requireExamTeacher(exam);
        prepareForWrite(request);
        ExamStatus status = ExamStatus.fromCode(exam.getStatus());
        if (status == ExamStatus.PUBLISHED || status == ExamStatus.CLOSED) {
            throw new BusinessException("已发布或已关闭的考试不允许修改");
        }
        ExamPaper paper = paperService.getEntityById(request.getPaperId());
        requirePublishedPaper(paper, "只能使用已发布的试卷，当前试卷状态：");
        fillExam(exam, request);
        exam.setUpdateTime(LocalDateTime.now());
        examSessionMapper.updateById(exam);
        return toResponse(exam);
    }

    @Transactional(readOnly = true)
    public ExamResponseDTO getById(Long id) {
        ExamSession exam = getEntityById(id);
        requireReadableExam(exam);
        return toResponse(exam);
    }

    @Transactional(readOnly = true)
    public List<ExamResponseDTO> list(Long teacherId) {
        return list(teacherId, null, null);
    }

    @Transactional(readOnly = true)
    public List<ExamResponseDTO> list(Long teacherId, String keyword, Long classId) {
        QueryWrapper<ExamSession> query = new QueryWrapper<>();
        var user = permissionService.currentUser();
        if (permissionService.isTeacher(user)) {
            query.eq("teacher_id", user.getUserId());
        } else if (permissionService.isStudent(user)) {
            List<Integer> classIds = permissionService.studentClassIds(user.getUserId());
            if (classIds.isEmpty()) {
                return List.of();
            }
            if (classId != null && classId > 0) {
                if (!classIds.contains(classId.intValue())) {
                    return List.of();
                }
                query.eq("class_id", classId);
            } else {
                query.in("class_id", classIds);
            }
            query.ne("status", ExamStatus.DRAFT.getCode());
        } else if (teacherId != null && teacherId > 0) {
            query.eq("teacher_id", teacherId);
        }
        if (classId != null && classId > 0 && !permissionService.isStudent(user)) {
            query.eq("class_id", classId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.like("title", keyword.trim());
        }
        query.orderByDesc("id");
        return examSessionMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExamResponseDTO publish(Long id) {
        ExamSession exam = getEntityById(id);
        permissionService.requireExamTeacher(exam);
        ExamStatus status = ExamStatus.fromCode(exam.getStatus());
        if (status != ExamStatus.DRAFT) {
            throw new BusinessException("只有草稿状态的考试才能发布，当前状态：" + status);
        }
        exam.setStatus(ExamStatus.PUBLISHED.getCode());
        exam.setUpdateTime(LocalDateTime.now());
        examSessionMapper.updateById(exam);
        return toResponse(exam);
    }

    @Transactional
    public void delete(Long id) {
        ExamSession exam = getEntityById(id);
        permissionService.requireExamTeacher(exam);
        ExamStatus status = ExamStatus.fromCode(exam.getStatus());
        if (status == ExamStatus.PUBLISHED) {
            throw new BusinessException("进行中的考试不允许删除，请先关闭");
        }
        examSessionMapper.deleteById(exam.getId());
    }

    @Transactional
    public ExamResponseDTO close(Long id) {
        ExamSession exam = getEntityById(id);
        permissionService.requireExamTeacher(exam);
        ExamStatus status = ExamStatus.fromCode(exam.getStatus());
        if (status != ExamStatus.PUBLISHED) {
            throw new BusinessException("只有已发布的考试才能关闭，当前状态：" + status);
        }
        exam.setStatus(ExamStatus.CLOSED.getCode());
        exam.setUpdateTime(LocalDateTime.now());
        examSessionMapper.updateById(exam);
        return toResponse(exam);
    }

    public ExamSession getEntityById(Long id) {
        ExamSession exam = examSessionMapper.selectById(toInt(id, "考试ID"));
        if (exam == null) {
            throw new BusinessException("考试不存在: " + id);
        }
        return exam;
    }

    public ExamResponseDTO toResponse(ExamSession exam) {
        ExamPaper paper = null;
        try {
            paper = paperService.getEntityById(toLong(exam.getPaperId()));
        } catch (BusinessException ignored) {
            // 试卷可能已被删除
        }
        ExamResponseDTO response = new ExamResponseDTO();
        response.setId(toLong(exam.getId()));
        response.setClassId(toLong(exam.getClassId()));
        response.setTeacherId(toLong(exam.getTeacherId()));
        response.setName(exam.getTitle());
        response.setPaperId(toLong(exam.getPaperId()));
        response.setPaperName(paper != null ? paper.getTitle() : "试卷已删除");
        response.setDescription(exam.getDescription());
        response.setStartTime(exam.getStartTime());
        response.setEndTime(exam.getEndTime());
        response.setDurationMinutes(exam.getDuration());
        response.setStatus(ExamStatus.fromCode(exam.getStatus()));
        response.setCreatedAt(exam.getCreateTime());
        response.setUpdatedAt(exam.getUpdateTime());
        return response;
    }

    private void validateRequest(ExamRequestDTO request) {
        if (request == null) {
            throw new BusinessException("考试信息不能为空");
        }
        if (request.getStartTime() == null) {
            throw new BusinessException("考试开始时间不能为空");
        }
        if (request.getEndTime() == null) {
            throw new BusinessException("考试结束时间不能为空");
        }
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException("考试结束时间不能早于开始时间");
        }
        if (request.getDurationMinutes() == null || request.getDurationMinutes() <= 0) {
            throw new BusinessException("考试时长必须大于0");
        }
        if (request.getStatus() == null) {
            throw new BusinessException("考试状态不能为空");
        }
    }

    private void prepareForWrite(ExamRequestDTO request) {
        permissionService.requireTeacherOrAdmin();
        if (request.getClassId() == null) {
            throw new BusinessException("教学班不能为空");
        }
        TeachingClass teachingClass = permissionService.getTeachingClass(toInt(request.getClassId(), "教学班ID"));
        ExamPaper paper = paperService.getEntityById(request.getPaperId());
        if (paper.getCourseId() == null || !paper.getCourseId().equals(teachingClass.getCourseId())) {
            throw new BusinessException("试卷课程与教学班课程不一致");
        }
        if (paper.getTeacherId() == null || !paper.getTeacherId().equals(teachingClass.getTeacherId())) {
            throw new BusinessException("试卷教师与教学班教师不一致");
        }
        var user = permissionService.currentUser();
        if (permissionService.isTeacher(user)) {
            if (!paper.getTeacherId().equals(user.getUserId())) {
                throw new com.cupk.aiacademy.common.ForbiddenException("无权使用该试卷创建考试");
            }
            permissionService.requireClassTeacher(teachingClass);
            request.setTeacherId(user.getUserId().longValue());
        } else {
            request.setTeacherId(paper.getTeacherId().longValue());
        }
    }

    private void requireReadableExam(ExamSession exam) {
        var user = permissionService.currentUser();
        if (permissionService.isAdmin(user)) {
            return;
        }
        if (permissionService.isTeacher(user)) {
            permissionService.requireExamTeacher(exam);
            return;
        }
        if (ExamStatus.fromCode(exam.getStatus()) == ExamStatus.DRAFT) {
            throw new com.cupk.aiacademy.common.ForbiddenException("无权查看该考试");
        }
        if (!permissionService.isStudentInClass(exam.getClassId(), user.getUserId())) {
            throw new com.cupk.aiacademy.common.ForbiddenException("无权查看该考试");
        }
    }

    private void requirePublishedPaper(ExamPaper paper, String messagePrefix) {
        PaperStatus status = PaperStatus.fromCode(paper.getStatus());
        if (status != PaperStatus.PUBLISHED) {
            throw new BusinessException(messagePrefix + status);
        }
    }

    private void fillExam(ExamSession exam, ExamRequestDTO request) {
        exam.setClassId(toNullableInt(request.getClassId(), "教学班ID"));
        exam.setTeacherId(toNullableInt(request.getTeacherId(), "教师ID"));
        exam.setPaperId(toInt(request.getPaperId(), "试卷ID"));
        exam.setTitle(request.getName());
        exam.setDescription(request.getDescription());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setDuration(request.getDurationMinutes());
        exam.setStatus(request.getStatus().getCode());
    }

    private Integer toNullableInt(Long value, String fieldName) {
        return value == null ? null : toInt(value, fieldName);
    }

    private Integer toInt(Long value, String fieldName) {
        if (value == null || value <= 0 || value > Integer.MAX_VALUE) {
            throw new BusinessException(fieldName + "不合法");
        }
        return value.intValue();
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
