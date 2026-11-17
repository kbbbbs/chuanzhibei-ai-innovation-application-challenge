package com.cupk.aiacademy.preparation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.rag.CourseKnowledgeService;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.service.CourseImportService;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.service.QuestionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;

/** Applies a prepared draft to its existing course without recreating that course or its lessons. */
@Service
public class PreparationCourseWorkflow {
    private final PreparationService preparations;
    private final CourseKnowledgeService knowledge;
    private final PermissionService permissions;
    private final ChapterMapper chapters;
    private final LessonMapper lessons;
    private final QuestionService questions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final NativeRagService rag;
    private final CourseImportService imports;

    public PreparationCourseWorkflow(PreparationService preparations, CourseKnowledgeService knowledge,
            PermissionService permissions, ChapterMapper chapters, LessonMapper lessons, QuestionService questions,
            JdbcTemplate jdbc, ObjectMapper json, NativeRagService rag, CourseImportService imports) {
        this.preparations = preparations; this.knowledge = knowledge; this.permissions = permissions;
        this.chapters = chapters; this.lessons = lessons; this.questions = questions; this.jdbc = jdbc;
        this.json = json; this.rag = rag; this.imports = imports;
    }

    @PostConstruct
    public void initialize() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS course_preparation_application (project_id BIGINT PRIMARY KEY, course_id INTEGER NOT NULL, before_json LONGTEXT NOT NULL, after_version VARCHAR(64) NOT NULL, result_json LONGTEXT NOT NULL, rolled_back BOOLEAN NOT NULL DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS course_page_import (request_id VARCHAR(100) PRIMARY KEY, course_id INTEGER NOT NULL, documents_json LONGTEXT NOT NULL, result_json LONGTEXT NOT NULL)");
    }

    public record Replacement(String lessonKey, Integer targetLessonId) { }
    public record ApplyRequest(String courseVersion, List<Replacement> replacements) { }
    public record ImportRequest(List<String> documentIds, String requestId) { }

    public Map<String, Object> preview(Long id) {
        var draft = preparations.get(id);
        int courseId = target(draft);
        var result = new LinkedHashMap<String, Object>();
        result.put("courseId", courseId);
        result.put("courseVersion", courseVersion(courseId));
        result.put("alreadyApplied", !applications(id).isEmpty());
        result.put("applicationId", applications(id).isEmpty() ? null : id);
        result.put("rolledBack", !applications(id).isEmpty() && Boolean.TRUE.equals(applications(id).getFirst().get("rolled_back")));
        result.put("chapters", draft.getOutline() == null ? List.of() : draft.getOutline().getChapters());
        result.put("existingLessons", jdbc.queryForList("SELECT l.id,l.chapter_id AS chapterId,c.title AS chapterTitle,l.title FROM t_lesson l JOIN t_chapter c ON c.id=l.chapter_id WHERE c.course_id=? ORDER BY c.sort_order,c.id,l.sort_order,l.id", courseId));
        result.put("questionsCount", draft.getQuestions().stream().mapToInt(group -> group.getQuestions().size()).sum());
        return result;
    }

    @Transactional
    public Map<String, Object> apply(Long id, ApplyRequest request) {
        var draft = preparations.get(id);
        int courseId = target(draft);
        lock(courseId);
        var existing = applications(id);
        if (!existing.isEmpty()) return applicationResult(existing.getFirst());
        if (request == null || !Objects.equals(request.courseVersion(), courseVersion(courseId)))
            throw new BusinessException("课程内容已变化，请重新预览后应用");
        preparations.markReady(id);
        draft = preparations.get(id);
        var mappings = replacements(draft, request.replacements(), courseId);
        var now = LocalDateTime.now();
        var before = new ArrayList<Lesson>();
        var newChapters = new ArrayList<Integer>();
        var newLessons = new ArrayList<Integer>();
        var newQuestions = new ArrayList<Long>();
        int chapterOrder = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0) FROM t_chapter WHERE course_id=?", Integer.class, courseId);
        Map<String, PreparationModels.LessonDraft> text = new LinkedHashMap<>();
        draft.getLessons().forEach(lesson -> text.put(lesson.getLessonKey(), lesson));
        for (var chapterDraft : draft.getOutline().getChapters()) {
            Chapter chapter = null;
            for (var skeleton : chapterDraft.getLessons()) {
                var content = text.get(skeleton.getLessonKey());
                Integer replacementId = mappings.get(skeleton.getLessonKey());
                if (replacementId != null) {
                    var lesson = lessons.selectById(replacementId);
                    before.add(json.convertValue(lesson, Lesson.class));
                    lesson.setTitle(skeleton.getTitle());
                    lesson.setContent(content.getContentMd());
                    lesson.setUpdatedAt(now);
                    lessons.updateById(lesson);
                    continue;
                }
                if (chapter == null) {
                    chapter = new Chapter(); chapter.setCourseId(courseId); chapter.setTitle(chapterDraft.getTitle());
                    chapter.setDescription(chapterDraft.getDescription()); chapter.setSortOrder(++chapterOrder);
                    chapter.setStatus(Chapter.STATUS_NORMAL); chapter.setCreatedAt(now); chapter.setUpdatedAt(now);
                    chapters.insert(chapter); newChapters.add(chapter.getId());
                }
                var lesson = new Lesson(); lesson.setChapterId(chapter.getId()); lesson.setTitle(skeleton.getTitle());
                lesson.setContent(content.getContentMd()); lesson.setResourceType("markdown");
                lesson.setDuration(skeleton.getDurationMinutes() == null ? 0 : skeleton.getDurationMinutes());
                lesson.setSortOrder(skeleton.getSortOrder()); lesson.setStatus(1); lesson.setViewCount(0);
                lesson.setVideoUrl(""); lesson.setCreatedAt(now); lesson.setUpdatedAt(now);
                lessons.insert(lesson); newLessons.add(lesson.getId());
            }
        }
        if (!Boolean.FALSE.equals(draft.getConfig().getIncludeQuestions())) {
            for (var group : draft.getQuestions()) for (var question : group.getQuestions())
                newQuestions.add(questions.createFromPreparation(question, courseId, draft.getTeacherId()).getId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", id); result.put("courseId", courseId);
        result.put("chaptersCreated", newChapters.size()); result.put("lessonsCreated", newLessons.size());
        result.put("lessonsReplaced", before.size()); result.put("questionsCreated", newQuestions.size()); result.put("rolledBack", false);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("replaced", before); snapshot.put("chapters", newChapters); snapshot.put("lessons", newLessons); snapshot.put("questions", newQuestions);
        jdbc.update("INSERT INTO course_preparation_application(project_id,course_id,before_json,after_version,result_json) VALUES(?,?,?,?,?)",
                id, courseId, write(snapshot), courseVersion(courseId), write(result));
        jdbc.update("UPDATE t_ai_preparation SET project_status='PUBLISHED',current_stage='PUBLISHED',published_course_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", courseId, id);
        return result;
    }

    @Transactional
    public Map<String, Object> rollback(Long id) {
        var draft = preparations.get(id);
        int courseId = target(draft);
        lock(courseId);
        var rows = applications(id);
        if (rows.isEmpty()) throw new BusinessException("该草稿尚未应用");
        var row = rows.getFirst();
        if (Boolean.TRUE.equals(row.get("rolled_back"))) return applicationResult(row);
        if (!Objects.equals(row.get("after_version"), courseVersion(courseId)))
            throw new BusinessException("应用后课程已有其他修改，请先核对课程内容，暂不能回退");
        var snapshot = read(String.valueOf(row.get("before_json")));
        var previous = json.convertValue(snapshot.get("replaced"), new TypeReference<List<Lesson>>() { });
        // Restore only the fields changed by this application, preserving video and student activity.
        for (var lesson : previous) jdbc.update("UPDATE t_lesson SET title=?,content=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                lesson.getTitle(), lesson.getContent(), lesson.getId());
        archive("t_lesson", snapshot.get("lessons"));
        archive("t_chapter", snapshot.get("chapters"));
        archive("t_question", snapshot.get("questions"));
        jdbc.update("UPDATE course_preparation_application SET rolled_back=TRUE WHERE project_id=?", id);
        var result = applicationResult(row); result.put("rolledBack", true);
        return result;
    }

    @Transactional
    public Map<String, Object> importPages(Integer courseId, ImportRequest request) {
        knowledge.requireCourseManager(courseId);
        if (request == null || request.requestId() == null || !request.requestId().matches("[a-zA-Z0-9-]{8,100}"))
            throw new BusinessException("请提供导入请求标识");
        if (request.documentIds() == null || request.documentIds().isEmpty() || request.documentIds().size() > 10)
            throw new BusinessException("请选择 1 至 10 份 PDF 或课件");
        lock(courseId);
        var previous = jdbc.queryForList("SELECT * FROM course_page_import WHERE request_id=?", request.requestId());
        if (!previous.isEmpty()) {
            var row = previous.getFirst();
            if (((Number) row.get("course_id")).intValue() != courseId || !Objects.equals(row.get("documents_json"), write(request.documentIds())))
                throw new BusinessException("导入标识已用于其他资料，请重新选择");
            return read(String.valueOf(row.get("result_json")));
        }
        String baseId = String.valueOf(knowledge.ensureCourseBase(courseId).get("id"));
        var documents = new ArrayList<CourseImportService.StoredCourseDocument>();
        for (String documentId : new java.util.LinkedHashSet<>(request.documentIds())) {
            var rows = jdbc.queryForList("SELECT kb_id,document_version FROM native_rag_document WHERE id=?", documentId);
            if (rows.isEmpty() || !baseId.equals(rows.getFirst().get("kb_id"))) throw new BusinessException("资料不属于当前课程");
            var file = rag.originalFile(documentId);
            int version = ((Number) rows.getFirst().get("document_version")).intValue();
            documents.add(new CourseImportService.StoredCourseDocument(file.path(), file.filename(),
                    "/api/rag/courses/" + courseId + "/documents/" + documentId + "/versions/" + version + "/download"));
        }
        var result = imports.appendStoredDocuments(permissions.getCourse(courseId), documents);
        jdbc.update("INSERT INTO course_page_import(request_id,course_id,documents_json,result_json) VALUES(?,?,?,?)",
                request.requestId(), courseId, write(request.documentIds()), write(result));
        return result;
    }

    private Map<String, Integer> replacements(PreparationModels.View draft, List<Replacement> requested, int courseId) {
        var available = new HashSet<String>();
        draft.getLessons().forEach(lesson -> available.add(lesson.getLessonKey()));
        var result = new LinkedHashMap<String, Integer>(); var targets = new HashSet<Integer>();
        for (var replacement : requested == null ? List.<Replacement>of() : requested) {
            if (replacement == null || !available.contains(replacement.lessonKey()) || replacement.targetLessonId() == null
                    || result.putIfAbsent(replacement.lessonKey(), replacement.targetLessonId()) != null || !targets.add(replacement.targetLessonId()))
                throw new BusinessException("替换目标重复或不存在");
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_lesson l JOIN t_chapter c ON c.id=l.chapter_id WHERE l.id=? AND c.course_id=?", Integer.class, replacement.targetLessonId(), courseId);
            if (count == null || count != 1) throw new BusinessException("替换课时不属于当前课程");
        }
        return result;
    }

    private int target(PreparationModels.View draft) {
        Integer courseId = draft.getConfig().getTargetCourseId();
        if (courseId == null) throw new BusinessException("请先把草稿关联到课程");
        knowledge.requireCourseManager(courseId); return courseId;
    }
    private void lock(int courseId) {
        jdbc.queryForList("SELECT id FROM t_course WHERE id=? FOR UPDATE", courseId);
        jdbc.queryForList("SELECT id FROM t_chapter WHERE course_id=? FOR UPDATE", courseId);
        jdbc.queryForList("SELECT l.id FROM t_lesson l JOIN t_chapter c ON c.id=l.chapter_id WHERE c.course_id=? FOR UPDATE", courseId);
    }
    private List<Map<String, Object>> applications(Long id) { return jdbc.queryForList("SELECT * FROM course_preparation_application WHERE project_id=?", id); }
    private Map<String, Object> applicationResult(Map<String, Object> row) {
        var result = read(String.valueOf(row.get("result_json"))); result.put("rolledBack", row.get("rolled_back")); return result;
    }
    private void archive(String table, Object ids) {
        for (Number id : json.convertValue(ids, new TypeReference<List<Long>>() { }))
            jdbc.update("UPDATE " + table + " SET status=0 WHERE id=?", id.longValue());
    }
    private String courseVersion(int courseId) {
        var state = new LinkedHashMap<String, Object>();
        state.put("course", jdbc.queryForList("SELECT id,title,description,status FROM t_course WHERE id=?", courseId));
        state.put("chapters", jdbc.queryForList("SELECT id,title,description,sort_order,status FROM t_chapter WHERE course_id=? ORDER BY id", courseId));
        state.put("lessons", jdbc.queryForList("SELECT l.id,l.chapter_id,l.title,l.content,l.resource_type,l.resource_url,l.video_url,l.video_resource_id,l.duration,l.sort_order,l.status FROM t_lesson l JOIN t_chapter c ON c.id=l.chapter_id WHERE c.course_id=? ORDER BY l.id", courseId));
        state.put("questions", jdbc.queryForList("SELECT id,title,options,answer,analysis,status FROM t_question WHERE course_id=? ORDER BY id", courseId));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(state).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private Map<String, Object> read(String value) {
        try { return json.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() { }); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
