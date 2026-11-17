package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/** Persists the reviewed target snapshot and serializes confirmation across processes. */
@Component
public class AgentActionSafetyService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private static final Map<String, String> TARGET_TABLES = Map.ofEntries(
            Map.entry("courseId","t_course"), Map.entry("chapterId","t_chapter"),
            Map.entry("lessonId","t_lesson"), Map.entry("classId","t_teaching_class"),
            Map.entry("teachingClassId","t_teaching_class"), Map.entry("classStudentId","t_class_student"),
            Map.entry("homeworkId","t_homework"), Map.entry("questionId","t_question"),
            Map.entry("noticeId","t_notice"), Map.entry("gradeRecordId","t_grade_record"),
            Map.entry("examSessionId","t_exam_session"), Map.entry("examPaperId","t_exam_paper"),
            Map.entry("paperId","t_exam_paper"), Map.entry("paperQuestionId","t_exam_paper_question"),
            Map.entry("homeworkSubmitId","t_homework_submit"), Map.entry("submissionId","t_exam_record"),
            Map.entry("examRecordId","t_exam_record"), Map.entry("submissionAnswerId","t_exam_answer"),
            Map.entry("applicationId","t_teacher_application"), Map.entry("categoryId","t_course_category"));
    private static final Map<String, String> PARENT_TABLES = Map.ofEntries(
            Map.entry("course_id", "t_course"), Map.entry("chapter_id", "t_chapter"),
            Map.entry("class_id", "t_teaching_class"), Map.entry("homework_id", "t_homework"),
            Map.entry("paper_id", "t_exam_paper"), Map.entry("exam_record_id", "t_exam_record"),
            Map.entry("exam_session_id", "t_exam_session"), Map.entry("paper_question_id", "t_exam_paper_question"),
            Map.entry("question_id", "t_question"));

    public AgentActionSafetyService(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json.copy().findAndRegisterModules();
    }

    @PostConstruct
    public void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS t_agent_action_execution (action_id VARCHAR(64) PRIMARY KEY, owner_id INT NOT NULL, "
                + "session_id INT, course_id INT, parameters_json MEDIUMTEXT NOT NULL, targets_json MEDIUMTEXT NOT NULL, result_json MEDIUMTEXT)");
        db.execute("CREATE TABLE IF NOT EXISTS t_agent_action_migration (id VARCHAR(40) PRIMARY KEY)");
        if (db.queryForObject("SELECT COUNT(*) FROM t_agent_action_migration WHERE id='unified_confirmation_v1'", Integer.class) == 0) {
            if (tableExists("t_agent_pending_action")) {
                db.update("UPDATE t_agent_pending_action SET expires_at=CURRENT_TIMESTAMP WHERE action_id NOT IN (SELECT action_id FROM t_agent_action_execution)");
            }
            db.update("INSERT INTO t_agent_action_migration(id) VALUES('unified_confirmation_v1')");
        }
    }

    public void register(AgentPendingAction action) {
        var targets = snapshots(action.actionCode(), action.parameters());
        db.update("INSERT INTO t_agent_action_execution(action_id,owner_id,parameters_json,targets_json) VALUES(?,?,?,?)",
                action.actionId(), action.createdBy(), write(action.parameters()), write(targets));
    }

    public void bind(String actionId, Integer sessionId, Integer courseId) {
        var rows = db.queryForList("SELECT * FROM t_agent_action_execution WHERE action_id=?", actionId);
        if (rows.isEmpty()) {
            throw new BusinessException("待确认操作需要重新预览");
        }
        var row = rows.getFirst();
        if (!Objects.equals(number(row.get("owner_id")), UserContext.getCurrentUserId())) {
            throw new BusinessException("无权绑定此操作");
        }
        var sessions = db.queryForList("SELECT user_id FROM t_agent_session WHERE id=?", sessionId);
        if (sessions.isEmpty() || !Objects.equals(number(sessions.getFirst().get("user_id")), UserContext.getCurrentUserId())) {
            throw new BusinessException("对话不存在或没有访问权限");
        }
        if (row.get("session_id") != null && !Objects.equals(number(row.get("session_id")), sessionId)) {
            throw new BusinessException("待确认操作已经绑定其他对话");
        }
        var targets = readMap(String.valueOf(row.get("targets_json")));
        if (courseId != null) {
            if (!targets.containsKey("t_course:" + courseId))
                throw new BusinessException("此操作未关联当前课程，请在通用对话中重新发起");
            for (String key : targets.keySet()) {
                if (key.startsWith("t_course:") && !key.equals("t_course:" + courseId)) {
                    throw new BusinessException("操作目标不属于当前课程，请在目标课程重新发起");
                }
            }
        }
        db.update("UPDATE t_agent_action_execution SET session_id=?,course_id=? WHERE action_id=?", sessionId, courseId, actionId);
    }

    /** Must run in the same transaction as the business executor and completion write. */
    public Optional<AgentActionExecutionResult> lockAndReadResult(String actionId, Integer ownerId) {
        var rows = db.queryForList("SELECT * FROM t_agent_action_execution WHERE action_id=? FOR UPDATE", actionId);
        if (rows.isEmpty()) {
            throw new BusinessException("旧操作已失效，请重新预览后确认");
        }
        var row = rows.getFirst();
        if (!Objects.equals(number(row.get("owner_id")), ownerId)) {
            throw new BusinessException("不能确认其他用户创建的操作");
        }
        if (row.get("session_id") == null) {
            throw new BusinessException("操作尚未绑定对话，请重新预览后确认");
        }
        var sessions = db.queryForList("SELECT user_id,status,course_id FROM t_agent_session WHERE id=?", row.get("session_id"));
        if (sessions.isEmpty() || !Objects.equals(number(sessions.getFirst().get("user_id")), ownerId)
                || !Objects.equals(number(sessions.getFirst().get("status")), 1)
                || !Objects.equals(number(sessions.getFirst().get("course_id")), number(row.get("course_id")))) {
            throw new BusinessException("对话范围已失效，请重新发起操作");
        }
        Object result = row.get("result_json");
        return result == null ? Optional.empty() : Optional.of(readResult(String.valueOf(result)));
    }

    public void validateTargets(AgentPendingAction action) {
        var row = db.queryForMap("SELECT * FROM t_agent_action_execution WHERE action_id=?", action.actionId());
        if (!json.valueToTree(action.parameters()).equals(json.valueToTree(readMap(String.valueOf(row.get("parameters_json")))))) {
            throw new BusinessException("操作参数已改变，请重新预览");
        }
        var current = snapshots(action.actionCode(), action.parameters());
        current.keySet().stream().sorted(Comparator.comparing((String key) -> !key.startsWith("t_course:"))
                .thenComparing(Comparator.naturalOrder())).forEach(key -> {
                    int separator = key.indexOf(':');
                    db.queryForList("SELECT id FROM " + key.substring(0, separator) + " WHERE id=? FOR UPDATE",
                            number(key.substring(separator + 1)));
                });
        current = snapshots(action.actionCode(), action.parameters());
        if (!json.valueToTree(current).equals(json.valueToTree(readMap(String.valueOf(row.get("targets_json")))))) {
            throw new BusinessException("操作对象已更新，请重新预览后确认");
        }
    }

    public void validateExecutionParameters(AgentPendingAction action, Map<String, Object> parameters) {
        var reviewed = snapshots(action.actionCode(), action.parameters());
        var resolved = snapshots(action.actionCode(), parameters);
        if (!reviewed.equals(resolved)) {
            throw new BusinessException("操作目标已经改变，请重新预览后确认");
        }
    }

    public void completed(String actionId, AgentActionExecutionResult result) {
        db.update("UPDATE t_agent_action_execution SET result_json=? WHERE action_id=?", write(result), actionId);
    }

    public boolean isCompleted(String actionId) {
        return db.queryForObject("SELECT COUNT(*) FROM t_agent_action_execution WHERE action_id=? AND result_json IS NOT NULL", Integer.class, actionId) > 0;
    }

    private Map<String, Object> snapshots(String actionCode, Map<String, Object> parameters) {
        Map<String, Object> result = new TreeMap<>();
        for (var entry : parameters.entrySet()) {
            String name = camel(entry.getKey());
            String table = TARGET_TABLES.get(name);
            if (table != null && entry.getValue() != null) {
                capture(result, table, number(entry.getValue()));
            }
            if (name.equals("classIds") && entry.getValue() instanceof List<?> ids) {
                for (Object id : ids) {
                    capture(result, "t_teaching_class", number(id));
                }
            }
            if (name.equals("items") && entry.getValue() instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> values) {
                        boolean lessonSort = actionCode.startsWith("lesson.");
                        String targetField = lessonSort ? "lessonId" : "chapterId";
                        Object id = values.containsKey(targetField) ? values.get(targetField) : values.get("id");
                        if (id != null) {
                            capture(result, lessonSort ? "t_lesson" : "t_chapter", number(id));
                        }
                    }
                }
            }
        }
        return result;
    }

    private void capture(Map<String, Object> targets, String table, Integer id) {
        if (id == null || id <= 0 || targets.containsKey(table + ":" + id)) {
            return;
        }
        var rows = db.queryForList("SELECT * FROM " + table + " WHERE id=?", id);
        if (rows.isEmpty()) {
            throw new BusinessException("操作对象不存在，请重新选择");
        }
        var row = rows.getFirst();
        Map<String, String> value = new TreeMap<>();
        row.forEach((key, item) -> value.put(key.toLowerCase(Locale.ROOT), item == null ? null : String.valueOf(item)));
        targets.put(table + ":" + id, value);
        for (var parent : PARENT_TABLES.entrySet()) {
            if (value.get(parent.getKey()) != null)
                capture(targets, parent.getValue(), number(value.get(parent.getKey())));
        }
    }

    private String camel(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char letter : value.toCharArray()) {
            if (letter == '_') {
                upper = true;
                continue;
            }
            result.append(upper ? Character.toUpperCase(letter) : letter);
            upper = false;
        }
        return result.toString();
    }
    private Integer number(Object value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException("操作目标编号无效");
        }
    }
    private boolean tableExists(String name) {
        return Boolean.TRUE.equals(db.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            try (var tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
                while (tables.next()) {
                    if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
                }
                return false;
            }
        }));
    }
    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("操作快照保存失败");
        }
    }
    private Map<String, Object> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException("操作快照无法读取，请重新预览");
        }
    }
    private AgentActionExecutionResult readResult(String value) {
        try {
            return json.readValue(value, AgentActionExecutionResult.class);
        } catch (Exception e) {
            throw new BusinessException("操作结果无法读取");
        }
    }
}
