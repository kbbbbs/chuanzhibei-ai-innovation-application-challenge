package com.cupk.aiacademy.agent.query;

import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.ExamRecordMapper;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NaturalLanguageQueryServiceTest {
    @Mock private NaturalLanguageEntityResolver entityResolver;
    @Mock private NaturalLanguageTimeParser timeParser;
    @Mock private AgentToolSupport support;
    @Mock private TeachingClassMapper teachingClassMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private HomeworkMapper homeworkMapper;
    @Mock private HomeworkSubmitMapper homeworkSubmitMapper;
    @Mock private GradeRecordMapper gradeRecordMapper;
    @Mock private LearningProgressMapper learningProgressMapper;
    @Mock private ChapterMapper chapterMapper;
    @Mock private LessonMapper lessonMapper;
    @Mock private ExamSessionMapper examSessionMapper;
    @Mock private ExamRecordMapper examRecordMapper;
    @Mock private UserMapper userMapper;

    private NaturalLanguageQueryService newService() {
        return new NaturalLanguageQueryService(entityResolver, timeParser, support,
                teachingClassMapper, classStudentMapper, homeworkMapper, homeworkSubmitMapper,
                gradeRecordMapper, learningProgressMapper, chapterMapper, lessonMapper,
                examSessionMapper, examRecordMapper, userMapper);
    }

    @Test
    void homeworkSubmissionStatusSummarizesCrossChainQuery() {
        Course course = new Course();
        course.setId(11);
        course.setTitle("Python 数据分析基础");
        when(entityResolver.resolveCourses(any(), anyInt())).thenReturn(List.of(course));
        when(entityResolver.resolveTeachingClasses(any(), anyList(), anyInt())).thenReturn(List.of());
        when(entityResolver.classIdsForCourses(anyList())).thenReturn(List.of(5));
        when(timeParser.parse(any())).thenReturn(new NaturalLanguageTimeRange(null, null, ""));

        Homework hw = new Homework();
        hw.setId(21);
        hw.setClassId(5);
        hw.setTitle("数据清洗");
        hw.setFullScore(new BigDecimal("100"));
        when(entityResolver.resolveHomeworks(any(), anyList(), any(), anyInt()))
                .thenReturn(List.of(hw));

        ClassStudent cs = new ClassStudent();
        cs.setClassId(5);
        cs.setStudentId(7);
        cs.setStatus(1);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(cs));

        HomeworkSubmit submit = new HomeworkSubmit();
        submit.setHomeworkId(21);
        submit.setStudentId(7);
        when(homeworkSubmitMapper.selectList(any())).thenReturn(List.of(submit));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("查 Python 数据分析基础 这门课的作业提交情况", 10);

        assertTrue(answer.contains("数据清洗"));
        assertTrue(answer.contains("已交人数=1") || answer.contains("提交率=100"));
        assertTrue(answer.contains("未交人数=0"));
    }

    @Test
    void examStatsComputesAverageAndLowScoreStudents() {
        when(entityResolver.resolveCourses(any(), anyInt())).thenReturn(List.of());
        when(entityResolver.resolveTeachingClasses(any(), anyList(), anyInt())).thenReturn(List.of());
        when(entityResolver.classIdsForCourses(anyList())).thenReturn(List.of());
        when(timeParser.parse(any())).thenReturn(new NaturalLanguageTimeRange(null, null, ""));

        ExamRecord high = new ExamRecord();
        high.setStudentId(7);
        high.setTotalScore(new BigDecimal("90"));
        ExamRecord mid = new ExamRecord();
        mid.setStudentId(8);
        mid.setTotalScore(new BigDecimal("70"));
        ExamRecord low = new ExamRecord();
        low.setStudentId(9);
        low.setTotalScore(new BigDecimal("30"));
        when(examRecordMapper.selectList(any())).thenReturn(List.of(high, mid, low));

        NaturalLanguageQueryService service = newService();
        String answer = service.answer("统计这门考试的平均分和低分学生", 10);

        assertTrue(answer.contains("平均分"));
        assertTrue(answer.contains("最高分=90"));
        assertTrue(answer.contains("低分学生数=1"));
        assertTrue(answer.contains("30"));
    }

    @Test
    void emptyQueryReturnsMessage() {
        NaturalLanguageQueryService service = newService();
        String answer = service.answer("", 10);
        assertTrue(answer.contains("为空") || answer.contains("空"));
    }
}
