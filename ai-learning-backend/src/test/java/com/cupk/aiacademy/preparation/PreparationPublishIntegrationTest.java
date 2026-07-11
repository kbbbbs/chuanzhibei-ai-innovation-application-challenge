package com.cupk.aiacademy.preparation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class PreparationPublishIntegrationTest {
    @Autowired private PreparationService preparationService;
    @Autowired private CourseMapper courseMapper;
    @Autowired private ChapterMapper chapterMapper;
    @Autowired private LessonMapper lessonMapper;
    @Autowired private QuestionMapper questionMapper;

    @BeforeEach
    void loginTeacher() {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(2);
        user.setUsername("teacher");
        user.setRoleCode("teacher");
        UserContext.set(user);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void publishesReviewedDraftAtomicallyAndIdempotently() {
        PreparationModels.Config config = new PreparationModels.Config();
        config.setTitle("智能备课集成测试课程");
        config.setCategoryId(1);
        config.setAudience("本科生");
        config.setChapterCount(1);
        config.setLessonsPerChapter(1);
        Long projectId = preparationService.create(config).getId();

        PreparationModels.LessonDraft skeleton = new PreparationModels.LessonDraft();
        skeleton.setLessonKey("lesson-1");
        skeleton.setTitle("第一课时");
        skeleton.setObjectives("掌握核心概念");
        skeleton.setKnowledgePoints(List.of("核心概念"));
        skeleton.setDurationMinutes(45);
        PreparationModels.ChapterDraft chapter = new PreparationModels.ChapterDraft();
        chapter.setChapterKey("chapter-1");
        chapter.setTitle("第一章");
        chapter.setLessons(new ArrayList<>(List.of(skeleton)));
        PreparationModels.Outline outline = new PreparationModels.Outline();
        outline.setCourseDescription("测试课程简介");
        outline.setChapters(new ArrayList<>(List.of(chapter)));
        preparationService.saveOutline(projectId, outline);

        PreparationModels.LessonDraft lesson = new PreparationModels.LessonDraft();
        lesson.setLessonKey("lesson-1");
        lesson.setTitle("第一课时");
        lesson.setDurationMinutes(45);
        lesson.setContentMd("# 第一课时\n\n这是经过教师审核的正文。");
        preparationService.saveLessons(projectId, List.of(lesson));

        QuestionRequestDTO question = new QuestionRequestDTO();
        question.setType(QuestionType.SHORT_ANSWER);
        question.setDifficulty(Difficulty.MEDIUM);
        question.setContent("请解释核心概念。");
        question.setKnowledgePoint("核心概念");
        QuestionRequestDTO.QuestionAnswerRequest answer = new QuestionRequestDTO.QuestionAnswerRequest();
        answer.setAnswerContent("核心概念答案");
        answer.setSortOrder(1);
        question.setAnswers(List.of(answer));
        PreparationModels.ChapterQuestions group = new PreparationModels.ChapterQuestions();
        group.setChapterKey("chapter-1");
        group.setChapterTitle("第一章");
        group.setQuestions(new ArrayList<>(List.of(question)));
        preparationService.saveQuestions(projectId, List.of(group));

        PreparationModels.PublishResult first = preparationService.publish(projectId);
        PreparationModels.PublishResult second = preparationService.publish(projectId);

        assertNotNull(first.getCourseId());
        assertEquals(first.getCourseId(), second.getCourseId());
        assertEquals(2, courseMapper.selectById(first.getCourseId()).getStatus());
        var chapters = chapterMapper.selectList(new QueryWrapper<com.cupk.aiacademy.pojo.Chapter>().eq("course_id", first.getCourseId()));
        assertEquals(1, chapters.size());
        assertEquals(1, lessonMapper.selectCount(new QueryWrapper<com.cupk.aiacademy.pojo.Lesson>().eq("chapter_id", chapters.getFirst().getId())));
        assertEquals(1, questionMapper.selectCount(new QueryWrapper<com.cupk.aiacademy.pojo.Question>().eq("course_id", first.getCourseId())));
    }
}
