package com.cupk.aiacademy.preparation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.service.QuestionService;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class PreparationService {
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String RUNNING = "RUNNING";
    private static final String FAILED = "FAILED";
    private static final String SUCCEEDED = "SUCCEEDED";

    private final PreparationMapper preparationMapper;
    private final CourseMapper courseMapper;
    private final CourseCategoryMapper categoryMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final QuestionService questionService;
    private final PermissionService permissionService;
    private final WeKnoraService weKnoraService;
    private final PreparationModelGateway modelGateway;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;
    private final int maxContextSnippets;

    public PreparationService(PreparationMapper preparationMapper,
                              CourseMapper courseMapper,
                              CourseCategoryMapper categoryMapper,
                              ChapterMapper chapterMapper,
                              LessonMapper lessonMapper,
                              QuestionService questionService,
                              PermissionService permissionService,
                              WeKnoraService weKnoraService,
                              PreparationModelGateway modelGateway,
                              ObjectMapper objectMapper,
                              @Qualifier("preparationTaskExecutor") Executor taskExecutor,
                              @Value("${preparation.max-context-snippets:10}") int maxContextSnippets) {
        this.preparationMapper = preparationMapper;
        this.courseMapper = courseMapper;
        this.categoryMapper = categoryMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.questionService = questionService;
        this.permissionService = permissionService;
        this.weKnoraService = weKnoraService;
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.maxContextSnippets = Math.max(1, Math.min(30, maxContextSnippets));
    }

    @PostConstruct
    public void recoverInterruptedJobs() {
        preparationMapper.update(null, new UpdateWrapper<PreparationProject>()
                .eq("generation_status", RUNNING)
                .set("generation_status", FAILED)
                .set("error_message", "服务重启导致生成中断，请重试")
                .set("updated_at", LocalDateTime.now()));
    }

    public PreparationModels.View create(PreparationModels.Config config) {
        permissionService.requireTeacherOrAdmin();
        validateConfig(config);
        var user = permissionService.currentUser();
        PreparationProject project = new PreparationProject();
        project.setTeacherId(user.getUserId());
        project.setTitle(config.getTitle().trim());
        project.setConfigJson(write(config));
        project.setKnowledgeBaseIdsJson(write(cleanIds(config.getKnowledgeBaseIds())));
        project.setSourceFilesJson("[]");
        project.setLessonsJson("[]");
        project.setQuestionsJson("[]");
        project.setProjectStatus(DRAFT);
        project.setCurrentStage("CONFIG");
        project.setGenerationStatus("IDLE");
        project.setProgress(0);
        project.setVersion(0);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(project.getCreatedAt());
        preparationMapper.insert(project);
        return toView(project, false);
    }

    public List<PreparationModels.View> list() {
        permissionService.requireTeacherOrAdmin();
        Integer userId = permissionService.currentUser().getUserId();
        return preparationMapper.selectList(new QueryWrapper<PreparationProject>()
                        .eq("teacher_id", userId)
                        .ne("project_status", "ABANDONED")
                        .orderByDesc("updated_at"))
                .stream().map(project -> toView(project, false)).toList();
    }

    public PreparationModels.View get(Long id) {
        return toView(requireOwned(id), true);
    }

    public PreparationModels.View updateConfig(Long id, PreparationModels.Config config) {
        validateConfig(config);
        PreparationProject project = requireEditable(id);
        requireNotRunning(project);
        project.setTitle(config.getTitle().trim());
        project.setConfigJson(write(config));
        project.setKnowledgeBaseIdsJson(write(cleanIds(config.getKnowledgeBaseIds())));
        project.setUpdatedAt(LocalDateTime.now());
        updateOrConflict(project);
        return toView(requireOwned(id), true);
    }

    public PreparationModels.View uploadSources(Long id, List<MultipartFile> files) {
        PreparationProject project = requireEditable(id);
        requireNotRunning(project);
        if (files == null || files.isEmpty()) throw new BusinessException("请选择要上传的资料");
        if (files.size() > 10) throw new BusinessException("单次最多上传10个文件");

        String kbId = project.getOwnedKnowledgeBaseId();
        if (kbId == null || kbId.isBlank()) {
            Result created = weKnoraService.createKnowledgeBase(
                    "智能备课-" + project.getTitle() + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")),
                    "智能备课项目 " + project.getId() + " 的专属资料库");
            if (!"200".equals(created.getCode())) throw new BusinessException(created.getMsg());
            kbId = findId(created.getData());
            if (kbId == null) throw new BusinessException("知识库创建成功但未返回ID");
            project.setOwnedKnowledgeBaseId(kbId);
            project.setUpdatedAt(LocalDateTime.now());
            updateOrConflict(project);
            project = requireProject(id);
        }

        List<PreparationModels.SourceFile> stored = readList(project.getSourceFilesJson(), new TypeReference<>() {});
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            try {
                Result uploaded = weKnoraService.uploadFile(kbId, file.getBytes(), file.getOriginalFilename());
                if (!"200".equals(uploaded.getCode())) throw new BusinessException(uploaded.getMsg());
                PreparationModels.SourceFile source = new PreparationModels.SourceFile();
                source.setName(file.getOriginalFilename());
                source.setKnowledgeId(findId(uploaded.getData()));
                source.setParseStatus("pending");
                source.setParseProgress(0);
                stored.add(source);
                project.setSourceFilesJson(write(stored));
                project.setUpdatedAt(LocalDateTime.now());
                updateOrConflict(project);
                project = requireProject(id);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException("上传资料失败: " + e.getMessage());
            }
        }
        return toView(requireOwned(id), true);
    }

    public PreparationModels.View startOutline(Long id) {
        PreparationProject project = requireEditable(id);
        requireNotRunning(project);
        ensureUploadedSourcesReady(project);
        markRunning(project, 5);
        submitGeneration(id, () -> generateOutline(id));
        return toView(requireOwned(id), false);
    }

    public PreparationModels.View saveOutline(Long id, PreparationModels.Outline outline) {
        PreparationProject project = requireEditable(id);
        requireNotRunning(project);
        normalizeOutline(outline, readConfig(project));
        project.setOutlineJson(write(outline));
        project.setCurrentStage("OUTLINE_REVIEW");
        project.setGenerationStatus(SUCCEEDED);
        project.setProgress(100);
        project.setErrorMessage(null);
        project.setUpdatedAt(LocalDateTime.now());
        updateOrConflict(project);
        return toView(requireOwned(id), false);
    }

    public PreparationModels.View startLessons(Long id) {
        PreparationProject project = requireEditable(id);
        requireNotRunning(project);
        PreparationModels.Outline outline = readOutline(project);
        normalizeOutline(outline, readConfig(project));
        ensureUploadedSourcesReady(project);
        markRunning(project, 0);
        submitGeneration(id, () -> generateLessons(id));
        return toView(requireOwned(id), false);
    }

    public PreparationModels.View saveLessons(Long id, List<PreparationModels.LessonDraft> lessons) {
        PreparationProject project = requireEditable(id);
        requireNotRunning(project);
        validateLessons(project, lessons);
        project.setLessonsJson(write(lessons));
        project.setCurrentStage("LESSON_REVIEW");
        project.setGenerationStatus(SUCCEEDED);
        project.setProgress(100);
        project.setErrorMessage(null);
        project.setUpdatedAt(LocalDateTime.now());
        updateOrConflict(project);
        return toView(requireOwned(id), false);
    }

    public PreparationModels.View startQuestions(Long id) {
        PreparationProject project = requireEditable(id);
        requireNotRunning(project);
        validateLessons(project, readLessons(project));
        ensureUploadedSourcesReady(project);
        markRunning(project, 0);
        submitGeneration(id, () -> generateQuestions(id));
        return toView(requireOwned(id), false);
    }

    public PreparationModels.View saveQuestions(Long id, List<PreparationModels.ChapterQuestions> questions) {
        PreparationProject project = requireEditable(id);
        requireNotRunning(project);
        validateQuestionGroups(project, questions);
        project.setQuestionsJson(write(questions));
        project.setCurrentStage("READY_TO_PUBLISH");
        project.setGenerationStatus(SUCCEEDED);
        project.setProgress(100);
        project.setErrorMessage(null);
        project.setUpdatedAt(LocalDateTime.now());
        updateOrConflict(project);
        return toView(requireOwned(id), false);
    }

    @Transactional
    public PreparationModels.PublishResult publish(Long id) {
        PreparationProject project = requireOwned(id);
        if (PUBLISHED.equals(project.getProjectStatus()) && project.getPublishedCourseId() != null) {
            return publishedSummary(project);
        }
        if (!"READY_TO_PUBLISH".equals(project.getCurrentStage())) {
            throw new BusinessException("请先完成章节、课时和题目审核");
        }
        PreparationModels.Config config = readConfig(project);
        PreparationModels.Outline outline = readOutline(project);
        List<PreparationModels.LessonDraft> lessonDrafts = readLessons(project);
        List<PreparationModels.ChapterQuestions> questionGroups = readQuestions(project);
        validateConfig(config);
        normalizeOutline(outline, config);
        validateLessons(project, lessonDrafts);
        validateQuestionGroups(project, questionGroups);

        var user = permissionService.currentUser();
        LocalDateTime now = LocalDateTime.now();
        Course course = new Course();
        course.setCategoryId(config.getCategoryId());
        course.setTeacherId(user.getUserId());
        course.setTeacher(user.getUsername());
        course.setTitle(config.getTitle().trim());
        course.setDescription(firstNonBlank(outline.getCourseDescription(), config.getDescription()));
        course.setDifficulty(normalizeCourseDifficulty(config.getDifficulty()));
        course.setStudentCount(0);
        course.setViewCount(0);
        course.setRating(BigDecimal.ZERO);
        course.setStatus(2);
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        courseMapper.insert(course);

        int lessonCount = 0;
        for (PreparationModels.ChapterDraft chapterDraft : outline.getChapters()) {
            Chapter chapter = new Chapter();
            chapter.setCourseId(course.getId());
            chapter.setTitle(limit(chapterDraft.getTitle(), 200));
            chapter.setDescription(limit(chapterDraft.getDescription(), 500));
            chapter.setSortOrder(chapterDraft.getSortOrder());
            chapter.setStatus(Chapter.STATUS_NORMAL);
            chapter.setCreatedAt(now);
            chapter.setUpdatedAt(now);
            chapterMapper.insert(chapter);
            for (PreparationModels.LessonDraft skeleton : chapterDraft.getLessons()) {
                PreparationModels.LessonDraft content = findLesson(lessonDrafts, skeleton.getLessonKey());
                Lesson lesson = new Lesson();
                lesson.setChapterId(chapter.getId());
                lesson.setTitle(limit(skeleton.getTitle(), 200));
                lesson.setResourceType("markdown");
                lesson.setContent(content.getContentMd().trim());
                lesson.setDuration(Math.max(0, defaultInt(skeleton.getDurationMinutes(), 45) * 60));
                lesson.setSortOrder(skeleton.getSortOrder());
                lesson.setViewCount(0);
                lesson.setStatus(1);
                lesson.setVideoUrl("");
                lesson.setCreatedAt(now);
                lesson.setUpdatedAt(now);
                lessonMapper.insert(lesson);
                lessonCount++;
            }
        }

        int questionCount = 0;
        for (PreparationModels.ChapterQuestions group : questionGroups) {
            for (QuestionRequestDTO question : group.getQuestions()) {
                if (question.getStatus() == null) question.setStatus(RecordStatus.ACTIVE);
                questionService.createFromPreparation(question, course.getId(), user.getUserId());
                questionCount++;
            }
        }

        project.setProjectStatus(PUBLISHED);
        project.setCurrentStage("PUBLISHED");
        project.setPublishedCourseId(course.getId());
        project.setGenerationStatus(SUCCEEDED);
        project.setProgress(100);
        project.setUpdatedAt(now);
        if (preparationMapper.updateById(project) == 0) throw new BusinessException("备课草稿已被其他操作修改，请刷新后重试");

        PreparationModels.PublishResult result = new PreparationModels.PublishResult();
        result.setCourseId(course.getId());
        result.setChaptersCreated(outline.getChapters().size());
        result.setLessonsCreated(lessonCount);
        result.setQuestionsCreated(questionCount);
        return result;
    }

    private void generateOutline(Long id) {
        try {
            PreparationProject project = requireProject(id);
            PreparationModels.Config config = readConfig(project);
            String context = knowledgeContext(project, config.getTitle() + " " + config.getTeachingGoals());
            String userPrompt = "课程名称：" + config.getTitle() + "\n目标对象：" + config.getAudience()
                    + "\n难度：" + config.getDifficulty() + "\n总学时：" + config.getTotalHours()
                    + "\n章节数：" + config.getChapterCount() + "\n每章课时数：" + config.getLessonsPerChapter()
                    + "\n教学目标：" + safe(config.getTeachingGoals()) + "\n补充要求：" + safe(config.getExtraInstructions())
                    + "\n参考资料：\n" + context
                    + "\n只输出JSON对象，必须包含courseDescription和chapters。每个章节包含chapterKey,title,description,sortOrder,lessons；"
                    + "每个课时包含lessonKey,title,objectives,knowledgePoints,durationMinutes,sortOrder。";
            PreparationModels.Outline outline = modelGateway.generateOutline(
                    "你是高校课程设计专家。输出可直接用于教学管理系统的结构化课程骨架，不输出JSON之外的内容。", userPrompt);
            normalizeOutline(outline, config);
            completeGeneration(id, "outline_json", write(outline), "OUTLINE_REVIEW");
        } catch (Exception e) {
            failGeneration(id, e);
        }
    }

    private void submitGeneration(Long id, Runnable generation) {
        try {
            taskExecutor.execute(generation);
        } catch (RuntimeException error) {
            failGeneration(id, error);
            throw new BusinessException("生成任务队列暂时繁忙，请稍后重试");
        }
    }

    private void generateLessons(Long id) {
        try {
            PreparationProject project = requireProject(id);
            PreparationModels.Config config = readConfig(project);
            PreparationModels.Outline outline = readOutline(project);
            List<PreparationModels.LessonDraft> generated = new ArrayList<>();
            int total = outline.getChapters().stream().mapToInt(ch -> ch.getLessons().size()).sum();
            int completed = 0;
            for (PreparationModels.ChapterDraft chapter : outline.getChapters()) {
                String context = knowledgeContext(project, chapter.getTitle() + " " + chapter.getDescription());
                for (PreparationModels.LessonDraft lesson : chapter.getLessons()) {
                    String markdown = modelGateway.generateMarkdown(
                            "你是高校教师备课助手。输出完整、严谨、可直接给学生阅读的Markdown课时内容，不输出代码围栏包裹全文。",
                            "课程：" + config.getTitle() + "\n章节：" + chapter.getTitle() + "\n课时：" + lesson.getTitle()
                                    + "\n目标：" + safe(lesson.getObjectives()) + "\n知识点：" + String.join("、", lesson.getKnowledgePoints())
                                    + "\n目标对象：" + config.getAudience() + "\n补充要求：" + safe(config.getExtraInstructions())
                                    + "\n参考资料：\n" + context
                                    + "\n内容至少包括：学习目标、核心讲解、示例或案例、课堂活动、总结与自测。");
                    PreparationModels.LessonDraft copy = copyLesson(lesson);
                    copy.setContentMd(markdown);
                    generated.add(copy);
                    completed++;
                    updateProgress(id, Math.max(1, completed * 100 / Math.max(1, total)));
                }
            }
            completeGeneration(id, "lessons_json", write(generated), "LESSON_REVIEW");
        } catch (Exception e) {
            failGeneration(id, e);
        }
    }

    private void generateQuestions(Long id) {
        try {
            PreparationProject project = requireProject(id);
            PreparationModels.Config config = readConfig(project);
            PreparationModels.Outline outline = readOutline(project);
            List<PreparationModels.LessonDraft> lessons = readLessons(project);
            List<PreparationModels.ChapterQuestions> groups = new ArrayList<>();
            int completed = 0;
            for (PreparationModels.ChapterDraft chapter : outline.getChapters()) {
                StringBuilder lessonContent = new StringBuilder();
                for (PreparationModels.LessonDraft skeleton : chapter.getLessons()) {
                    PreparationModels.LessonDraft lesson = findLesson(lessons, skeleton.getLessonKey());
                    lessonContent.append("\n## ").append(lesson.getTitle()).append("\n").append(lesson.getContentMd());
                }
                PreparationModels.QuestionConfig questionConfig = config.getQuestionConfig();
                String context = knowledgeContext(project, chapter.getTitle());
                List<QuestionRequestDTO> questions = modelGateway.generateQuestions(
                        "你是EduLoop题库构建助手。严格依据章节内容生成题目，只输出JSON数组。",
                        "课程：" + config.getTitle() + "\n章节：" + chapter.getTitle()
                                + "\n生成数量：" + questionConfig.getCountPerChapter()
                                + "\n允许题型：" + String.join("、", questionConfig.getTypes())
                                + "\n难度：" + questionConfig.getDifficulty()
                                + "\n补充要求：" + safe(questionConfig.getInstructions())
                                + "\n参考资料：\n" + context + "\n课时Markdown：\n" + lessonContent
                                + "\n字段：content,type,difficulty,knowledgePoint,analysis,options,answers。"
                                + "选择题必须有options；TRUE_FALSE固定A正确/B错误；answers元素包含answerContent和sortOrder。非选择题不得包含options。");
                normalizeQuestions(questions, questionConfig, chapter.getTitle());
                PreparationModels.ChapterQuestions group = new PreparationModels.ChapterQuestions();
                group.setChapterKey(chapter.getChapterKey());
                group.setChapterTitle(chapter.getTitle());
                group.setQuestions(questions);
                groups.add(group);
                completed++;
                updateProgress(id, completed * 100 / Math.max(1, outline.getChapters().size()));
            }
            completeGeneration(id, "questions_json", write(groups), "QUESTION_REVIEW");
        } catch (Exception e) {
            failGeneration(id, e);
        }
    }

    private void validateConfig(PreparationModels.Config config) {
        if (config == null || config.getTitle() == null || config.getTitle().isBlank()) throw new BusinessException("课程名称不能为空");
        if (config.getCategoryId() == null || categoryMapper.selectById(config.getCategoryId()) == null) throw new BusinessException("请选择有效课程分类");
        if (config.getAudience() == null || config.getAudience().isBlank()) throw new BusinessException("目标对象不能为空");
        if (defaultInt(config.getChapterCount(), 0) < 1 || config.getChapterCount() > 20) throw new BusinessException("章节数范围为1到20");
        if (defaultInt(config.getLessonsPerChapter(), 0) < 1 || config.getLessonsPerChapter() > 10) throw new BusinessException("每章课时数范围为1到10");
        if (config.getQuestionConfig() == null) config.setQuestionConfig(new PreparationModels.QuestionConfig());
        if (defaultInt(config.getQuestionConfig().getCountPerChapter(), 0) < 1 || config.getQuestionConfig().getCountPerChapter() > 20) {
            throw new BusinessException("每章题目数范围为1到20");
        }
    }

    private void normalizeOutline(PreparationModels.Outline outline, PreparationModels.Config config) {
        if (outline == null || outline.getChapters() == null || outline.getChapters().isEmpty()) throw new BusinessException("章节骨架不能为空");
        if (outline.getChapters().size() > 20) throw new BusinessException("章节不能超过20个");
        Set<String> chapterKeys = new LinkedHashSet<>();
        Set<String> lessonKeys = new LinkedHashSet<>();
        for (int i = 0; i < outline.getChapters().size(); i++) {
            PreparationModels.ChapterDraft chapter = outline.getChapters().get(i);
            if (chapter.getTitle() == null || chapter.getTitle().isBlank()) throw new BusinessException("章节名称不能为空");
            chapter.setChapterKey(uniqueKey(chapter.getChapterKey(), "chapter", chapterKeys));
            chapter.setSortOrder(i + 1);
            if (chapter.getLessons() == null || chapter.getLessons().isEmpty()) throw new BusinessException("每个章节至少需要一个课时");
            if (chapter.getLessons().size() > 10) throw new BusinessException("每章课时不能超过10个");
            for (int j = 0; j < chapter.getLessons().size(); j++) {
                PreparationModels.LessonDraft lesson = chapter.getLessons().get(j);
                if (lesson.getTitle() == null || lesson.getTitle().isBlank()) throw new BusinessException("课时名称不能为空");
                lesson.setLessonKey(uniqueKey(lesson.getLessonKey(), "lesson", lessonKeys));
                lesson.setSortOrder(j + 1);
                if (lesson.getKnowledgePoints() == null) lesson.setKnowledgePoints(new ArrayList<>());
                if (lesson.getDurationMinutes() == null || lesson.getDurationMinutes() <= 0) lesson.setDurationMinutes(45);
            }
        }
        if (outline.getCourseDescription() == null || outline.getCourseDescription().isBlank()) outline.setCourseDescription(config.getDescription());
    }

    private void validateLessons(PreparationProject project, List<PreparationModels.LessonDraft> lessons) {
        PreparationModels.Outline outline = readOutline(project);
        int expected = outline.getChapters().stream().mapToInt(ch -> ch.getLessons().size()).sum();
        if (lessons == null || lessons.size() != expected) throw new BusinessException("课时内容数量与骨架不一致");
        Set<String> keys = new LinkedHashSet<>();
        for (PreparationModels.LessonDraft lesson : lessons) {
            if (lesson.getLessonKey() == null || !keys.add(lesson.getLessonKey())) throw new BusinessException("课时标识重复或缺失");
            if (lesson.getContentMd() == null || lesson.getContentMd().isBlank()) throw new BusinessException("每个课时都必须包含Markdown内容");
        }
        for (PreparationModels.ChapterDraft chapter : outline.getChapters()) {
            for (PreparationModels.LessonDraft skeleton : chapter.getLessons()) findLesson(lessons, skeleton.getLessonKey());
        }
    }

    private void validateQuestionGroups(PreparationProject project, List<PreparationModels.ChapterQuestions> groups) {
        PreparationModels.Outline outline = readOutline(project);
        if (groups == null || groups.size() != outline.getChapters().size()) throw new BusinessException("题目分组必须覆盖全部章节");
        for (PreparationModels.ChapterDraft chapter : outline.getChapters()) {
            PreparationModels.ChapterQuestions group = groups.stream()
                    .filter(item -> Objects.equals(item.getChapterKey(), chapter.getChapterKey())).findFirst()
                    .orElseThrow(() -> new BusinessException("缺少章节题目：" + chapter.getTitle()));
            if (group.getQuestions() == null || group.getQuestions().isEmpty()) throw new BusinessException("每章至少保留一道题：" + chapter.getTitle());
            for (QuestionRequestDTO question : group.getQuestions()) questionService.validateForPaperComposition(question);
        }
    }

    private void normalizeQuestions(List<QuestionRequestDTO> questions, PreparationModels.QuestionConfig config, String chapterTitle) {
        if (questions == null || questions.isEmpty()) throw new BusinessException("章节未生成任何题目：" + chapterTitle);
        if (questions.size() > 20) questions.subList(20, questions.size()).clear();
        Set<String> allowed = new LinkedHashSet<>(config.getTypes());
        Difficulty fallback;
        try { fallback = Difficulty.valueOf(config.getDifficulty().toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { fallback = Difficulty.MEDIUM; }
        for (QuestionRequestDTO question : questions) {
            if (question.getType() == null || !allowed.contains(question.getType().name())) throw new BusinessException("模型返回了未允许的题型");
            if (question.getDifficulty() == null) question.setDifficulty(fallback);
            if (question.getStatus() == null) question.setStatus(RecordStatus.ACTIVE);
            questionService.validateForPaperComposition(question);
        }
    }

    private String knowledgeContext(PreparationProject project, String query) {
        List<String> ids = allKnowledgeBaseIds(project);
        if (ids.isEmpty()) return "（未绑定资料，请依据课程配置生成）";
        try {
            Result result = weKnoraService.search(new WeKnoraSearchRequest(ids, query, null, null));
            if (!"200".equals(result.getCode()) || result.getData() == null) return "（知识库检索暂不可用）";
            List<String> snippets = new ArrayList<>();
            collectSnippets(objectMapper.valueToTree(result.getData()), snippets);
            return snippets.isEmpty() ? "（未检索到相关片段）" : String.join("\n", snippets);
        } catch (Exception e) {
            return "（知识库检索暂不可用）";
        }
    }

    private void collectSnippets(JsonNode node, List<String> snippets) {
        if (node == null || node.isNull() || snippets.size() >= maxContextSnippets) return;
        if (node.isArray()) {
            node.forEach(child -> collectSnippets(child, snippets));
        } else if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectSnippets(entry.getValue(), snippets));
        } else if (node.isTextual()) {
            String text = node.asText().replaceAll("\\s+", " ").trim();
            if (text.length() >= 20 && text.length() <= 2000 && !snippets.contains(text)) snippets.add(text);
        }
    }

    private void ensureUploadedSourcesReady(PreparationProject project) {
        List<PreparationModels.SourceFile> configured = readList(project.getSourceFilesJson(), new TypeReference<>() {});
        if (configured.isEmpty()) return;
        SourceSnapshot snapshot = sourceSnapshot(project);
        if (!snapshot.ready) throw new BusinessException("上传资料仍在解析，请等待完成后再生成");
    }

    private SourceSnapshot sourceSnapshot(PreparationProject project) {
        List<PreparationModels.SourceFile> configured = readList(project.getSourceFilesJson(), new TypeReference<>() {});
        if (configured.isEmpty() || project.getOwnedKnowledgeBaseId() == null) return new SourceSnapshot(configured, true);
        try {
            Result result = weKnoraService.listKnowledge(project.getOwnedKnowledgeBaseId(), 100, 0);
            if (!"200".equals(result.getCode())) return new SourceSnapshot(configured, false);
            List<PreparationModels.SourceFile> current = parseSourceFiles(result.getData());
            boolean ready = current.size() >= configured.size() && current.stream().allMatch(file -> "success".equalsIgnoreCase(file.getParseStatus()));
            return new SourceSnapshot(current.isEmpty() ? configured : current, ready);
        } catch (Exception e) {
            return new SourceSnapshot(configured, false);
        }
    }

    private List<PreparationModels.SourceFile> parseSourceFiles(Object data) {
        JsonNode root = objectMapper.valueToTree(data);
        JsonNode items = root.isArray() ? root : root.path("items");
        List<PreparationModels.SourceFile> files = new ArrayList<>();
        if (!items.isArray()) return files;
        for (JsonNode item : items) {
            PreparationModels.SourceFile file = new PreparationModels.SourceFile();
            file.setName(text(item, "name", text(item, "fileName", text(item, "filename", "资料"))));
            file.setKnowledgeId(text(item, "id", text(item, "knowledgeId", null)));
            file.setParseStatus(text(item, "parseStatus", "pending"));
            file.setParseProgress(item.path("parseProgress").asInt("success".equals(file.getParseStatus()) ? 100 : 0));
            file.setErrorMessage(text(item, "errorMessage", null));
            files.add(file);
        }
        return files;
    }

    private PreparationModels.View toView(PreparationProject project, boolean refreshSources) {
        PreparationModels.View view = new PreparationModels.View();
        view.setId(project.getId());
        view.setTeacherId(project.getTeacherId());
        view.setTitle(project.getTitle());
        view.setConfig(readConfig(project));
        view.setKnowledgeBaseIds(readList(project.getKnowledgeBaseIdsJson(), new TypeReference<>() {}));
        view.setOwnedKnowledgeBaseId(project.getOwnedKnowledgeBaseId());
        SourceSnapshot sources = refreshSources ? sourceSnapshot(project)
                : new SourceSnapshot(readList(project.getSourceFilesJson(), new TypeReference<>() {}), true);
        view.setSourceFiles(sources.files);
        view.setSourcesReady(sources.ready);
        view.setOutline(readNullable(project.getOutlineJson(), PreparationModels.Outline.class));
        view.setLessons(readList(project.getLessonsJson(), new TypeReference<>() {}));
        view.setQuestions(readList(project.getQuestionsJson(), new TypeReference<>() {}));
        view.setProjectStatus(project.getProjectStatus());
        view.setCurrentStage(project.getCurrentStage());
        view.setGenerationStatus(project.getGenerationStatus());
        view.setProgress(project.getProgress());
        view.setErrorMessage(project.getErrorMessage());
        view.setPublishedCourseId(project.getPublishedCourseId());
        view.setVersion(project.getVersion());
        view.setCreatedAt(project.getCreatedAt());
        view.setUpdatedAt(project.getUpdatedAt());
        return view;
    }

    private PreparationProject requireOwned(Long id) {
        permissionService.requireTeacherOrAdmin();
        PreparationProject project = requireProject(id);
        if (!Objects.equals(project.getTeacherId(), permissionService.currentUser().getUserId())) throw new BusinessException("无权访问该备课项目");
        return project;
    }

    private PreparationProject requireEditable(Long id) {
        PreparationProject project = requireOwned(id);
        if (!DRAFT.equals(project.getProjectStatus())) throw new BusinessException("已发布项目不能修改");
        return project;
    }

    private PreparationProject requireProject(Long id) {
        PreparationProject project = preparationMapper.selectById(id);
        if (project == null) throw new BusinessException("备课项目不存在");
        return project;
    }

    private void requireNotRunning(PreparationProject project) {
        if (RUNNING.equals(project.getGenerationStatus())) throw new BusinessException("当前阶段正在生成，请稍候");
    }

    private void markRunning(PreparationProject project, int progress) {
        project.setGenerationStatus(RUNNING);
        project.setProgress(progress);
        project.setErrorMessage(null);
        project.setUpdatedAt(LocalDateTime.now());
        updateOrConflict(project);
    }

    private void completeGeneration(Long id, String jsonColumn, String json, String stage) {
        preparationMapper.update(null, new UpdateWrapper<PreparationProject>()
                .eq("id", id).eq("project_status", DRAFT)
                .set(jsonColumn, json)
                .set("current_stage", stage)
                .set("generation_status", SUCCEEDED)
                .set("progress", 100)
                .set("error_message", null)
                .set("updated_at", LocalDateTime.now()));
    }

    private void failGeneration(Long id, Exception error) {
        preparationMapper.update(null, new UpdateWrapper<PreparationProject>()
                .eq("id", id)
                .set("generation_status", FAILED)
                .set("error_message", limit(rootMessage(error), 2000))
                .set("updated_at", LocalDateTime.now()));
    }

    private void updateProgress(Long id, int progress) {
        preparationMapper.update(null, new UpdateWrapper<PreparationProject>()
                .eq("id", id).eq("generation_status", RUNNING)
                .set("progress", Math.max(0, Math.min(99, progress)))
                .set("updated_at", LocalDateTime.now()));
    }

    private void updateOrConflict(PreparationProject project) {
        if (preparationMapper.updateById(project) == 0) throw new BusinessException("备课草稿已被其他操作修改，请刷新后重试");
    }

    private PreparationModels.PublishResult publishedSummary(PreparationProject project) {
        PreparationModels.PublishResult result = new PreparationModels.PublishResult();
        result.setCourseId(project.getPublishedCourseId());
        PreparationModels.Outline outline = readOutline(project);
        result.setChaptersCreated(outline.getChapters().size());
        result.setLessonsCreated(outline.getChapters().stream().mapToInt(ch -> ch.getLessons().size()).sum());
        result.setQuestionsCreated(readQuestions(project).stream().mapToInt(group -> group.getQuestions().size()).sum());
        return result;
    }

    private PreparationModels.Config readConfig(PreparationProject project) { return read(project.getConfigJson(), PreparationModels.Config.class); }
    private PreparationModels.Outline readOutline(PreparationProject project) {
        PreparationModels.Outline value = readNullable(project.getOutlineJson(), PreparationModels.Outline.class);
        if (value == null) throw new BusinessException("请先生成并审核章节骨架");
        return value;
    }
    private List<PreparationModels.LessonDraft> readLessons(PreparationProject project) { return readList(project.getLessonsJson(), new TypeReference<>() {}); }
    private List<PreparationModels.ChapterQuestions> readQuestions(PreparationProject project) { return readList(project.getQuestionsJson(), new TypeReference<>() {}); }

    private List<String> allKnowledgeBaseIds(PreparationProject project) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(readList(project.getKnowledgeBaseIdsJson(), new TypeReference<>() {}));
        if (project.getOwnedKnowledgeBaseId() != null && !project.getOwnedKnowledgeBaseId().isBlank()) ids.add(project.getOwnedKnowledgeBaseId());
        return ids.stream().filter(id -> id != null && !id.isBlank()).toList();
    }

    private List<String> cleanIds(List<String> ids) {
        if (ids == null) return new ArrayList<>();
        return ids.stream().filter(Objects::nonNull).map(String::trim).filter(id -> !id.isEmpty()).distinct().toList();
    }

    private PreparationModels.LessonDraft copyLesson(PreparationModels.LessonDraft source) {
        return read(write(source), PreparationModels.LessonDraft.class);
    }

    private PreparationModels.LessonDraft findLesson(List<PreparationModels.LessonDraft> lessons, String key) {
        return lessons.stream().filter(item -> Objects.equals(item.getLessonKey(), key)).findFirst()
                .orElseThrow(() -> new BusinessException("缺少课时内容：" + key));
    }

    private String uniqueKey(String value, String prefix, Set<String> used) {
        String key = value == null || value.isBlank() ? prefix + "-" + UUID.randomUUID() : value.trim();
        while (!used.add(key)) key = prefix + "-" + UUID.randomUUID();
        return key;
    }

    private String findId(Object data) {
        if (data == null) return null;
        if (data instanceof String text && !text.isBlank()) return text;
        JsonNode root = objectMapper.valueToTree(data);
        return findId(root);
    }

    private String findId(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            for (String key : List.of("id", "knowledgeBaseId", "knowledge_base_id", "knowledgeId")) {
                JsonNode value = node.get(key);
                if (value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText();
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                String value = findId(fields.next().getValue());
                if (value != null) return value;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String value = findId(child);
                if (value != null) return value;
            }
        }
        return null;
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new BusinessException("备课草稿序列化失败"); }
    }

    private <T> T read(String json, Class<T> type) {
        try { return objectMapper.readValue(json, type); }
        catch (Exception e) { throw new BusinessException("备课草稿数据损坏"); }
    }

    private <T> T readNullable(String json, Class<T> type) {
        return json == null || json.isBlank() ? null : read(json, type);
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return objectMapper.readValue(json, type); }
        catch (Exception e) { throw new BusinessException("备课草稿列表数据损坏"); }
    }

    private int defaultInt(Integer value, int fallback) { return value == null ? fallback : value; }
    private String safe(String value) { return value == null || value.isBlank() ? "无" : value.trim(); }
    private String firstNonBlank(String first, String second) { return first != null && !first.isBlank() ? first.trim() : safe(second); }
    private String normalizeCourseDifficulty(String value) {
        String normalized = value == null ? "beginner" : value.toLowerCase(Locale.ROOT);
        return Set.of("beginner", "intermediate", "advanced").contains(normalized) ? normalized : "beginner";
    }
    private String limit(String value, int max) { return value == null ? null : value.length() <= max ? value : value.substring(0, max); }
    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }

    private record SourceSnapshot(List<PreparationModels.SourceFile> files, boolean ready) { }
}
