package com.cupk.aiacademy.questiongen;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.GenerateQuestionsFromKbRequest;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.rag.RagAccessException;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.service.QuestionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * Knowledge-base question generation as a background task with a stored draft.
 *
 * Questions are generated in small batches and appended to the draft as each batch finishes, so a failure or restart
 * keeps what was made and a resume asks only for the remainder. Saving to the question bank marks each draft question,
 * so saving again never writes the same question twice.
 */
@Service
public class QuestionGenerationTaskService {
    private static final int BATCH = 5;
    private static final Set<String> DRAFT_ONLY_FIELDS = Set.of("draftId", "saved", "savedId", "saveError");
    private final JdbcTemplate db;
    private final QuestionService questions;
    private final PermissionService permissions;
    private final NativeRagService rag;
    private final ObjectMapper json;
    private final ObjectMapper lenient;
    private final Executor executor;

    public QuestionGenerationTaskService(JdbcTemplate db, QuestionService questions, PermissionService permissions,
                                         NativeRagService rag, ObjectMapper json,
                                         @Qualifier("preparationTaskExecutor") Executor executor) {
        this.db = db;
        this.questions = questions;
        this.permissions = permissions;
        this.rag = rag;
        this.json = json;
        // Drafts come back from the editor and may carry display-only fields.
        this.lenient = json.copy().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.executor = executor;
    }

    @PostConstruct
    public void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS t_question_generation_task (id VARCHAR(36) PRIMARY KEY, actor_id INT NOT NULL, "
                + "teacher_id BIGINT NOT NULL, kb_id VARCHAR(36) NOT NULL, course_id BIGINT NOT NULL, request_json TEXT NOT NULL, "
                + "status VARCHAR(16) NOT NULL, requested INT NOT NULL, questions_json MEDIUMTEXT NOT NULL, error_message VARCHAR(1000), "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.update("UPDATE t_question_generation_task SET status='INCOMPLETE', error_message='服务重启导致生成中断，可以继续补生成' WHERE status='RUNNING'");
    }

    public Map<String, Object> start(GenerateQuestionsFromKbRequest request) {
        permissions.requireTeacherOrAdmin();
        if (request == null || request.getKnowledgeBaseId() == null || request.getKnowledgeBaseId().isBlank()) throw new BusinessException("知识库不能为空");
        if (request.getCourseId() == null) throw new BusinessException("请选择课程");
        var course = permissions.getCourse(request.getCourseId().intValue());
        AuthUtils.LoginUser user = permissions.currentUser();
        long teacherId;
        if ("teacher".equals(user.getRoleCode())) {
            permissions.requireCourseTeacher(course);
            teacherId = user.getUserId();
        } else teacherId = course.getTeacherId();
        rag.requireReadableBaseIdsForOwner(List.of(request.getKnowledgeBaseId()), user.getUserId());
        int count = request.getCount() == null || request.getCount() < 1 ? 5 : Math.min(20, request.getCount());
        request.setCount(count);
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO t_question_generation_task(id,actor_id,teacher_id,kb_id,course_id,request_json,status,requested,questions_json) VALUES(?,?,?,?,?,?,'RUNNING',?,'[]')",
                id, user.getUserId(), teacherId, request.getKnowledgeBaseId(), request.getCourseId(), write(request), count);
        submit(id);
        return view(row(id));
    }

    public Map<String, Object> get(String id) { return view(owned(id)); }

    /** The current teacher's recent tasks for one knowledge base, newest first. */
    public List<Map<String, Object>> list(String knowledgeBaseId) {
        return db.queryForList("SELECT * FROM t_question_generation_task WHERE actor_id=? AND kb_id=? AND status<>'DISCARDED' ORDER BY created_at DESC LIMIT 20",
                permissions.currentUser().getUserId(), knowledgeBaseId).stream().map(this::view).toList();
    }

    public Map<String, Object> resume(String id) {
        var task = owned(id);
        if (!"INCOMPLETE".equals(task.get("status"))) throw new BusinessException("只有未完成的任务可以继续生成");
        db.update("UPDATE t_question_generation_task SET status='RUNNING',error_message=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='INCOMPLETE'", id);
        submit(id);
        return view(row(id));
    }

    /** Edits replace unsaved drafts; questions already in the bank stay exactly as saved. */
    public Map<String, Object> updateDrafts(String id, List<Map<String, Object>> edited) {
        var task = owned(id);
        if ("RUNNING".equals(task.get("status"))) throw new BusinessException("题目仍在生成，完成后再编辑");
        if (edited == null) throw new BusinessException("题目列表不能为空");
        Map<String, Map<String, Object>> stored = new LinkedHashMap<>();
        for (var draft : drafts(task)) stored.put(String.valueOf(draft.get("draftId")), draft);
        List<Map<String, Object>> next = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (var item : edited) {
            String key = String.valueOf(item.get("draftId"));
            var before = stored.get(key);
            if (before != null && Boolean.TRUE.equals(before.get("saved"))) next.add(before);
            else {
                var copy = new LinkedHashMap<>(item);
                copy.remove("saved");
                copy.remove("savedId");
                if (before == null) copy.put("draftId", UUID.randomUUID().toString());
                next.add(copy);
            }
            seen.add(key);
        }
        // Saved questions cannot be removed from the record of what was written to the bank.
        for (var entry : stored.entrySet())
            if (!seen.contains(entry.getKey()) && Boolean.TRUE.equals(entry.getValue().get("saved"))) next.add(entry.getValue());
        db.update("UPDATE t_question_generation_task SET questions_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", write(next), id);
        return view(row(id));
    }

    /** Writes every unsaved draft to the bank, one at a time, recording each result before the next. */
    public Map<String, Object> save(String id) {
        var task = owned(id);
        if ("RUNNING".equals(task.get("status"))) throw new BusinessException("题目仍在生成，完成后再保存");
        List<Map<String, Object>> drafts = drafts(task);
        int savedNow = 0, failedNow = 0;
        for (var draft : drafts) {
            if (Boolean.TRUE.equals(draft.get("saved"))) continue;
            try {
                Map<String, Object> plain = new LinkedHashMap<>(draft);
                DRAFT_ONLY_FIELDS.forEach(plain::remove);
                var created = questions.create(lenient.convertValue(plain, QuestionRequestDTO.class));
                draft.put("saved", true);
                draft.put("savedId", created == null ? null : created.getId());
                draft.remove("saveError");
                savedNow++;
            } catch (RuntimeException error) {
                draft.put("saveError", error.getMessage() == null ? "保存失败" : error.getMessage());
                failedNow++;
            }
            db.update("UPDATE t_question_generation_task SET questions_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", write(drafts), id);
        }
        boolean allSaved = !drafts.isEmpty() && drafts.stream().allMatch(draft -> Boolean.TRUE.equals(draft.get("saved")));
        if (allSaved) db.update("UPDATE t_question_generation_task SET status='SAVED',updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        Map<String, Object> view = view(row(id));
        view.put("savedNow", savedNow);
        view.put("failedNow", failedNow);
        return view;
    }

    public Map<String, Object> discard(String id) {
        owned(id);
        db.update("UPDATE t_question_generation_task SET status='DISCARDED',updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        return view(row(id));
    }

    private void submit(String id) {
        try { executor.execute(() -> run(id)); }
        catch (RuntimeException busy) {
            finish(id, "INCOMPLETE", "生成任务队列暂时繁忙，可以稍后继续生成");
            throw new BusinessException("生成任务队列暂时繁忙，请稍后重试");
        }
    }

    private void run(String id) {
        try {
            var task = row(id);
            // The persisted requester is re-validated; a disabled account or lost course stops the task.
            UserContext.set(permissions.resolveActiveUser(((Number) task.get("actor_id")).intValue()));
            var request = json.readValue(task.get("request_json").toString(), GenerateQuestionsFromKbRequest.class);
            long teacherId = ((Number) task.get("teacher_id")).longValue();
            int requested = ((Number) task.get("requested")).intValue();
            while (true) {
                var current = row(id);
                if (!"RUNNING".equals(current.get("status"))) return;
                List<Map<String, Object>> drafts = drafts(current);
                int missing = requested - drafts.size();
                if (missing <= 0) break;
                request.setCount(Math.min(BATCH, missing));
                List<QuestionRequestDTO> generated = questions.generateFromKnowledgeBase(request, teacherId);
                Set<String> known = new HashSet<>();
                drafts.forEach(draft -> known.add(String.valueOf(draft.get("content")).trim()));
                int added = 0;
                for (QuestionRequestDTO question : generated == null ? List.<QuestionRequestDTO>of() : generated) {
                    if (drafts.size() >= requested || question.getContent() == null || !known.add(question.getContent().trim())) continue;
                    Map<String, Object> draft = json.convertValue(question, new TypeReference<>() {});
                    draft.put("draftId", UUID.randomUUID().toString());
                    drafts.add(draft);
                    added++;
                }
                if (added == 0) throw new BusinessException("资料中没有更多可出的不重复题目");
                db.update("UPDATE t_question_generation_task SET questions_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='RUNNING'", write(drafts), id);
            }
            finish(id, "READY", null);
        } catch (Exception error) {
            finish(id, "INCOMPLETE", error instanceof BusinessException || error instanceof RagAccessException || error instanceof ForbiddenException
                    ? error.getMessage() : "生成过程失败，请检查资料和模型配置后继续生成");
        } finally {
            UserContext.clear();
        }
    }

    private void finish(String id, String status, String error) {
        db.update("UPDATE t_question_generation_task SET status=?,error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='RUNNING'",
                status, error == null ? null : error.substring(0, Math.min(error.length(), 1000)), id);
    }

    private Map<String, Object> owned(String id) {
        var task = row(id);
        if (!Objects.equals(((Number) task.get("actor_id")).intValue(), permissions.currentUser().getUserId()))
            throw new ForbiddenException("只能查看自己发起的出题任务");
        return task;
    }

    private Map<String, Object> row(String id) {
        var rows = db.queryForList("SELECT * FROM t_question_generation_task WHERE id=?", id);
        if (rows.isEmpty()) throw new BusinessException("出题任务不存在");
        return rows.getFirst();
    }

    private List<Map<String, Object>> drafts(Map<String, Object> task) {
        try { return new ArrayList<>(json.readValue(task.get("questions_json").toString(), new TypeReference<List<Map<String, Object>>>() {})); }
        catch (Exception unreadable) { throw new BusinessException("出题草稿无法读取"); }
    }

    private Map<String, Object> view(Map<String, Object> task) {
        List<Map<String, Object>> drafts = drafts(task);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", task.get("id"));
        view.put("knowledgeBaseId", task.get("kb_id"));
        view.put("courseId", task.get("course_id"));
        view.put("status", task.get("status"));
        view.put("requested", task.get("requested"));
        view.put("generated", drafts.size());
        view.put("saved", (int) drafts.stream().filter(draft -> Boolean.TRUE.equals(draft.get("saved"))).count());
        view.put("errorMessage", task.get("error_message"));
        view.put("createdAt", task.get("created_at"));
        view.put("updatedAt", task.get("updated_at"));
        view.put("questions", drafts);
        return view;
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("出题任务无法保存"); }
    }
}
