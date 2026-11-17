package com.cupk.aiacademy.preparation;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.CourseCategory;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.service.QuestionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Per-item persistence, resume, edit protection and continuous generation, with writes applied to one stored project. */
class PreparationResumeTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PreparationMapper mapper = mock(PreparationMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final NativeRagService rag = mock(NativeRagService.class);
    private final PreparationModelGateway model = mock(PreparationModelGateway.class);
    private final QuestionService questions = mock(QuestionService.class);
    private final CourseCategoryMapper categories = mock(CourseCategoryMapper.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final PreparationService service = new PreparationService(mapper, mock(CourseMapper.class), categories,
            mock(ChapterMapper.class), mock(LessonMapper.class), questions, permissions, rag, model, json, queued::add, 10);
    private PreparationProject project;

    @BeforeEach
    void setup() throws Exception {
        project = new PreparationProject();
        project.setId(1L);
        project.setTeacherId(7);
        project.setTitle("人工智能入门");
        project.setConfigJson(json.writeValueAsString(config(true)));
        project.setKnowledgeBaseIdsJson("[]");
        project.setSourceFilesJson("[]");
        project.setLessonsJson("[]");
        project.setQuestionsJson("[]");
        project.setProjectStatus("DRAFT");
        project.setCurrentStage("CONFIG");
        project.setGenerationStatus("IDLE");
        project.setProgress(0);
        project.setVersion(0);
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(7);
        user.setRoleCode("teacher");
        UserContext.set(user);
        when(permissions.currentUser()).thenReturn(user);
        when(categories.selectById(1)).thenReturn(new CourseCategory());
        when(mapper.selectById(1L)).thenAnswer(call -> project);
        when(mapper.updateById(any(PreparationProject.class))).thenReturn(1);
        doAnswer(call -> { apply(call.getArgument(1)); return 1; }).when(mapper).update(isNull(), any());
    }

    @AfterEach
    void clear() { UserContext.clear(); }

    @Test
    void aFailedLessonKeepsTheFinishedOnesAndResumingFillsOnlyTheGap() throws Exception {
        project.setOutlineJson(json.writeValueAsString(outline("l1", "l2", "l3")));
        project.setCurrentStage("OUTLINE_REVIEW");
        AtomicBoolean secondFails = new AtomicBoolean(true);
        when(model.generateMarkdown(anyString(), anyString())).thenAnswer(call -> {
            String prompt = call.getArgument(1);
            if (prompt.contains("课时：l2") && secondFails.get()) throw new IllegalStateException("provider down");
            return "正文 " + prompt.substring(prompt.indexOf("课时：") + 3, prompt.indexOf("课时：") + 5);
        });

        service.startLessons(1L);
        runQueued();

        List<PreparationModels.LessonDraft> lessons = lessons();
        assertEquals(List.of("GENERATED", "FAILED", "GENERATED"), lessons.stream().map(PreparationModels.LessonDraft::getStatus).toList());
        assertTrue(lessons.get(0).getContentMd().startsWith("正文 l1"));
        assertTrue(lessons.get(2).getContentMd().startsWith("正文 l3"));
        assertEquals("FAILED", project.getGenerationStatus());
        assertEquals("LESSON_REVIEW", project.getCurrentStage());
        assertTrue(project.getErrorMessage().contains("1 个课时"));

        secondFails.set(false);
        service.startLessons(1L, "missing");
        runQueued();

        assertEquals(List.of("GENERATED", "GENERATED", "GENERATED"), lessons().stream().map(PreparationModels.LessonDraft::getStatus).toList());
        verify(model, times(1)).generateMarkdown(anyString(), contains("课时：l1"));
        verify(model, times(2)).generateMarkdown(anyString(), contains("课时：l2"));
        assertEquals("SUCCEEDED", project.getGenerationStatus());
    }

    @Test
    void editedLessonsSurviveRegenerationAndASingleRewriteKeepsThePreviousVersion() throws Exception {
        project.setOutlineJson(json.writeValueAsString(outline("l1", "l2")));
        project.setCurrentStage("OUTLINE_REVIEW");
        when(model.generateMarkdown(anyString(), anyString())).thenReturn("第一版", "第一版", "第二版", "第三版");
        service.startLessons(1L);
        runQueued();

        List<PreparationModels.LessonDraft> edited = lessons();
        edited.getFirst().setContentMd("教师改写的正文");
        service.saveLessons(1L, edited);
        assertTrue(Boolean.TRUE.equals(lessons().getFirst().getEdited()));
        assertNull(lessons().get(1).getEdited());

        service.startLessons(1L, "all");
        runQueued();
        assertEquals("教师改写的正文", lessons().getFirst().getContentMd());
        assertTrue(lessons().get(1).getContentMd().startsWith("第二版"));
        assertTrue(lessons().get(1).getPreviousContentMd().startsWith("第一版"));

        service.regenerateLesson(1L, "l1");
        runQueued();
        assertTrue(lessons().getFirst().getContentMd().startsWith("第三版"));
        assertEquals("教师改写的正文", lessons().getFirst().getPreviousContentMd());
        assertNotEquals(Boolean.TRUE, lessons().getFirst().getEdited());
    }

    @Test
    void continuousGenerationWithoutQuestionsEndsReadyToPublishAndPublishesNoQuestions() throws Exception {
        project.setConfigJson(json.writeValueAsString(config(false)));
        when(model.generateOutline(anyString(), anyString())).thenReturn(outline("l1", "l2"));
        when(model.generateMarkdown(anyString(), anyString())).thenReturn("课时正文");

        service.startAll(1L, true);
        runQueued();

        assertEquals("READY_TO_PUBLISH", project.getCurrentStage());
        assertEquals("SUCCEEDED", project.getGenerationStatus());
        assertEquals(2, lessons().size());
        verify(model, never()).generateQuestions(anyString(), anyString());
        service.publish(1L);
        verify(questions, never()).createFromPreparation(any(), any(), any());
    }

    @Test
    void continuousGenerationStopsAtAFailedChapterAndResumesWithoutRedoingFinishedWork() throws Exception {
        PreparationModels.Outline twoChapters = outline("l1");
        PreparationModels.ChapterDraft second = new PreparationModels.ChapterDraft();
        second.setChapterKey("c2");
        second.setTitle("第二章");
        second.setLessons(new ArrayList<>(List.of(lesson("l2"))));
        twoChapters.getChapters().add(second);
        when(model.generateOutline(anyString(), anyString())).thenReturn(twoChapters);
        when(model.generateMarkdown(anyString(), anyString())).thenReturn("课时正文");
        AtomicBoolean secondChapterFails = new AtomicBoolean(true);
        when(model.generateQuestions(anyString(), anyString())).thenAnswer(call -> {
            if (((String) call.getArgument(1)).contains("章节：第二章") && secondChapterFails.get()) throw new IllegalStateException("down");
            return List.of(question());
        });

        service.startAll(1L, true);
        runQueued();
        assertEquals("QUESTION_REVIEW", project.getCurrentStage());
        assertEquals("FAILED", project.getGenerationStatus());
        assertEquals(List.of("GENERATED", "FAILED"), groups().stream().map(PreparationModels.ChapterQuestions::getStatus).toList());

        secondChapterFails.set(false);
        service.startAll(1L, false);
        runQueued();
        assertEquals("READY_TO_PUBLISH", project.getCurrentStage());
        verify(model, times(1)).generateOutline(anyString(), anyString());
        verify(model, times(2)).generateMarkdown(anyString(), anyString());
        verify(model, times(1)).generateQuestions(anyString(), contains("章节：人工智能绪论"));
    }

    @Test
    void savingQuestionsKeepsTheStageAndMarkingReadyChecksTheChosenDeliverables() throws Exception {
        project.setOutlineJson(json.writeValueAsString(outline("l1")));
        project.setCurrentStage("QUESTION_REVIEW");
        PreparationModels.LessonDraft lesson = lesson("l1");
        lesson.setContentMd("正文");
        project.setLessonsJson(json.writeValueAsString(List.of(lesson)));
        PreparationModels.ChapterQuestions empty = new PreparationModels.ChapterQuestions();
        empty.setChapterKey("c1");
        empty.setChapterTitle("人工智能绪论");

        service.saveQuestions(1L, List.of(empty));
        assertEquals("QUESTION_REVIEW", project.getCurrentStage());
        assertThrows(BusinessException.class, () -> service.markReady(1L));

        project.setConfigJson(json.writeValueAsString(config(false)));
        service.markReady(1L);
        assertEquals("READY_TO_PUBLISH", project.getCurrentStage());
    }

    private void runQueued() {
        while (!queued.isEmpty()) queued.removeFirst().run();
    }

    private List<PreparationModels.LessonDraft> lessons() throws Exception {
        return json.readValue(project.getLessonsJson(), new TypeReference<>() {});
    }

    private List<PreparationModels.ChapterQuestions> groups() throws Exception {
        return json.readValue(project.getQuestionsJson(), new TypeReference<>() {});
    }

    /** Applies the SET part of an UpdateWrapper to the stored project; conditions are ignored. */
    private void apply(UpdateWrapper<PreparationProject> wrapper) {
        Map<String, Object> values = wrapper.getParamNameValuePairs();
        for (String part : wrapper.getSqlSet().split(",")) {
            String column = part.substring(0, part.indexOf('=')).trim();
            String key = part.substring(part.indexOf("paramNameValuePairs.") + 20, part.indexOf('}'));
            Object value = values.get(key);
            switch (column) {
                case "outline_json" -> project.setOutlineJson((String) value);
                case "lessons_json" -> project.setLessonsJson((String) value);
                case "questions_json" -> project.setQuestionsJson((String) value);
                case "current_stage" -> project.setCurrentStage((String) value);
                case "generation_status" -> project.setGenerationStatus((String) value);
                case "progress" -> project.setProgress((Integer) value);
                case "error_message" -> project.setErrorMessage((String) value);
                default -> { }
            }
        }
    }

    private PreparationModels.Config config(boolean includeQuestions) {
        PreparationModels.Config config = new PreparationModels.Config();
        config.setTitle("人工智能入门");
        config.setCategoryId(1);
        config.setAudience("本科生");
        config.setChapterCount(1);
        config.setLessonsPerChapter(1);
        config.setIncludeQuestions(includeQuestions);
        return config;
    }

    private PreparationModels.LessonDraft lesson(String key) {
        PreparationModels.LessonDraft lesson = new PreparationModels.LessonDraft();
        lesson.setLessonKey(key);
        lesson.setTitle(key);
        return lesson;
    }

    private PreparationModels.Outline outline(String... lessonKeys) {
        PreparationModels.ChapterDraft chapter = new PreparationModels.ChapterDraft();
        chapter.setChapterKey("c1");
        chapter.setTitle("人工智能绪论");
        for (String key : lessonKeys) chapter.getLessons().add(lesson(key));
        PreparationModels.Outline outline = new PreparationModels.Outline();
        outline.setChapters(new ArrayList<>(List.of(chapter)));
        return outline;
    }

    private QuestionRequestDTO question() {
        QuestionRequestDTO question = new QuestionRequestDTO();
        question.setContent("什么是智能？");
        question.setType(QuestionType.SHORT_ANSWER);
        return question;
    }
}
