package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.common.SubmissionStatus;
import com.cupk.aiacademy.dto.ExamResultResponseDTO;
import com.cupk.aiacademy.dto.exports.ExamScoreExportRow;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.service.imports.TabularFileSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExamResultService {
    private static final BigDecimal PASS_RATIO = new BigDecimal("0.60");

    private final ExamService examService;
    private final PermissionService permissionService;
    private final PaperService paperService;
    private final ClassStudentMapper classStudentMapper;
    private final ExamRecordMapper examRecordMapper;
    private final UserMapper userMapper;

    public ExamResultService(ExamService examService,
                             PermissionService permissionService,
                             PaperService paperService,
                             ClassStudentMapper classStudentMapper,
                             ExamRecordMapper examRecordMapper,
                             UserMapper userMapper) {
        this.examService = examService;
        this.permissionService = permissionService;
        this.paperService = paperService;
        this.classStudentMapper = classStudentMapper;
        this.examRecordMapper = examRecordMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public ExamResultResponseDTO submit(Long examId) {
        ExamSession exam = requireExamTeacher(examId);
        ExamStatus currentStatus = ExamStatus.fromCode(exam.getStatus());
        boolean manuallyClosed = currentStatus == ExamStatus.CLOSED || currentStatus == ExamStatus.RESULTS_SUBMITTED;
        if (!manuallyClosed && (exam.getEndTime() == null || LocalDateTime.now().isBefore(exam.getEndTime()))) {
            throw new BusinessException("考试结束后才能提交成绩");
        }
        if (currentStatus == ExamStatus.DRAFT) {
            throw new BusinessException("未发布的考试不能提交成绩");
        }
        if (currentStatus != ExamStatus.RESULTS_SUBMITTED) {
            exam.setStatus(ExamStatus.RESULTS_SUBMITTED.getCode());
            exam.setUpdateTime(LocalDateTime.now());
            examService.updateResultStatus(exam);
        }
        return buildResults(exam);
    }

    @Transactional(readOnly = true)
    public ExamResultResponseDTO getResults(Long examId) {
        ExamSession exam = requireExamTeacher(examId);
        if (ExamStatus.fromCode(exam.getStatus()) != ExamStatus.RESULTS_SUBMITTED) {
            throw new BusinessException("考试成绩尚未提交");
        }
        return buildResults(exam);
    }

    @Transactional(readOnly = true)
    public TabularFileSupport.ExportFile export(Long examId, String format) {
        ExamResultResponseDTO results = getResults(examId);
        List<ExamScoreExportRow> rows = results.getStudentScores().stream().map(score -> {
            ExamScoreExportRow row = new ExamScoreExportRow();
            row.setStudentId(score.getStudentId());
            row.setStudentName(score.getStudentName());
            row.setExamName(results.getExamName());
            row.setTotalScore(score.getTotalScore());
            row.setStatus(score.isAbsent() ? "未参加" : score.isCheated() ? "作弊" : submissionStatusName(score.getSubmissionStatus()));
            return row;
        }).toList();
        return TabularFileSupport.exportFile("考试成绩_" + examId, format, ExamScoreExportRow.class, rows);
    }

    private ExamSession requireExamTeacher(Long examId) {
        ExamSession exam = examService.getEntityById(examId);
        permissionService.requireExamTeacher(exam);
        return exam;
    }

    private ExamResultResponseDTO buildResults(ExamSession exam) {
        List<ClassStudent> members = classStudentMapper.selectList(new QueryWrapper<ClassStudent>()
                .eq("class_id", exam.getClassId())
                .eq("status", 1)
                .orderByAsc("student_id"));
        Map<Integer, ExamRecord> recordByStudentId = examRecordMapper.selectList(new QueryWrapper<ExamRecord>()
                        .eq("exam_session_id", exam.getId()))
                .stream()
                .filter(record -> record.getStudentId() != null)
                .collect(Collectors.toMap(ExamRecord::getStudentId, Function.identity(), (first, ignored) -> first));
        Map<Integer, User> users = members.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(members.stream()
                                .map(ClassStudent::getStudentId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        BigDecimal fullScore = paperService.getEntityById(exam.getPaperId().longValue()).getTotalScore();
        BigDecimal safeFullScore = fullScore == null ? BigDecimal.ZERO : fullScore;

        List<ExamResultResponseDTO.StudentScore> scores = members.stream()
                .map(member -> toStudentScore(member, users.get(member.getStudentId()), recordByStudentId.get(member.getStudentId())))
                .toList();
        List<BigDecimal> values = scores.stream()
                .filter(score -> !score.isCheated())
                .map(ExamResultResponseDTO.StudentScore::getTotalScore)
                .toList();

        ExamResultResponseDTO response = new ExamResultResponseDTO();
        response.setExamId(exam.getId().longValue());
        response.setExamName(exam.getTitle());
        response.setStatus(ExamStatus.fromCode(exam.getStatus()));
        response.setFullScore(safeFullScore);
        response.setStudentScores(scores);
        response.setAverageScore(average(values));
        response.setHighestScore(values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        response.setLowestScore(values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        response.setPassRate(passRate(values, safeFullScore));
        return response;
    }

    private ExamResultResponseDTO.StudentScore toStudentScore(ClassStudent member, User user, ExamRecord record) {
        ExamResultResponseDTO.StudentScore score = new ExamResultResponseDTO.StudentScore();
        score.setStudentId(member.getStudentId().longValue());
        score.setStudentName(user == null || user.getNickname() == null || user.getNickname().isBlank()
                ? "学生" + member.getStudentId()
                : user.getNickname());
        score.setAbsent(record == null);
        score.setTotalScore(record == null || record.getTotalScore() == null ? BigDecimal.ZERO : record.getTotalScore());
        if (record != null) {
            score.setSubmissionId(record.getId().longValue());
            score.setSubmissionStatus(SubmissionStatus.fromCode(record.getStatus()));
            score.setCheated(record.getCheated() != null && record.getCheated() == 1);
            score.setScreenSwitchCount(record.getScreenSwitchCount() == null ? 0 : record.getScreenSwitchCount());
        }
        return score;
    }

    private BigDecimal average(List<BigDecimal> scores) {
        if (scores.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal passRate(List<BigDecimal> scores, BigDecimal fullScore) {
        if (scores.isEmpty() || fullScore.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal passScore = fullScore.multiply(PASS_RATIO);
        long passed = scores.stream().filter(score -> score.compareTo(passScore) >= 0).count();
        return BigDecimal.valueOf(passed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private String submissionStatusName(SubmissionStatus status) {
        if (status == null || status == SubmissionStatus.IN_PROGRESS) {
            return "未提交";
        }
        if (status == SubmissionStatus.GRADED) {
            return "已批改";
        }
        return "待批改";
    }
}
