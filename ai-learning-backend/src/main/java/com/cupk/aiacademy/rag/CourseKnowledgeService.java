package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.service.PermissionService;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Course ownership is independent of student document grants. */
@Service
public class CourseKnowledgeService {
    private final JdbcTemplate jdbc;
    private final PermissionService permissions;
    public CourseKnowledgeService(JdbcTemplate jdbc, PermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @PostConstruct public void initialize() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS course_knowledge_base (course_id INTEGER PRIMARY KEY, kb_id VARCHAR(36) NOT NULL UNIQUE, archived BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS course_creation_request (owner_id INTEGER NOT NULL, request_key VARCHAR(100) NOT NULL, course_id INTEGER NOT NULL, PRIMARY KEY(owner_id,request_key))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS course_library_assignment (legacy_kb_id VARCHAR(36) PRIMARY KEY, course_id INTEGER NOT NULL, snapshot_json LONGTEXT NOT NULL, assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }

    public void requireCourseManager(Integer courseId) {
        var actor = UserContext.get();
        if (actor == null) throw new RagAccessException("401", "请先登录");
        var rows = jdbc.queryForList("SELECT teacher_id FROM t_course WHERE id=?", courseId);
        if (rows.isEmpty()) throw new RagAccessException("404", "课程不存在");
        if (!("teacher".equals(actor.getRoleCode())
                && Objects.equals(actor.getUserId(), ((Number) rows.getFirst().get("teacher_id")).intValue())))
            throw new RagAccessException("403", "无权管理该课程资料");
    }

    public boolean isCourseManager(Integer courseId, Integer userId) {
        var actor = UserContext.get();
        if (actor == null || !Objects.equals(actor.getUserId(), userId)) return false;
        try { requireCourseManager(courseId); return true; }
        catch (RagAccessException denied) { return false; }
    }

    @Transactional public Map<String,Object> ensureCourseBase(Integer courseId) {
        requireCourseManager(courseId);
        return ensureBase(courseId);
    }

    private Map<String,Object> ensureBase(Integer courseId) {
        var course = jdbc.queryForMap("SELECT * FROM t_course WHERE id=? FOR UPDATE", courseId);
        var links = jdbc.queryForList("SELECT * FROM course_knowledge_base WHERE course_id=?", courseId);
        String kb;
        if (links.isEmpty()) {
            kb = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO native_rag_base(id,owner_id,name,description,shared) VALUES(?,?,?,?,FALSE)",
                    kb, course.get("teacher_id"), course.get("title"), "课程专属资料");
            jdbc.update("INSERT INTO course_knowledge_base(course_id,kb_id) VALUES(?,?)", courseId, kb);
        } else {
            if (Boolean.TRUE.equals(links.getFirst().get("archived"))) throw new RagAccessException("409", "课程资料已归档");
            kb = links.getFirst().get("kb_id").toString();
        }
        var result = new LinkedHashMap<String,Object>();
        jdbc.queryForMap("SELECT * FROM native_rag_base WHERE id=?", kb).forEach((key,value) -> result.put(key.toLowerCase(java.util.Locale.ROOT),value));
        result.put("course_id", courseId);
        result.put("read_only", false);
        return result;
    }

    public String baseId(Integer courseId) { return ensureCourseBase(courseId).get("id").toString(); }

    public Integer courseForBase(String kbId) {
        var rows = jdbc.queryForList("SELECT course_id FROM course_knowledge_base WHERE kb_id=? AND archived=FALSE", kbId);
        return rows.isEmpty() ? null : ((Number) rows.getFirst().get("course_id")).intValue();
    }

    public void lockCreationOwner() {
        jdbc.queryForList("SELECT id FROM t_user WHERE id=? FOR UPDATE", UserContext.getCurrentUserId());
    }

    public Integer createdCourse(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.length() > 100) throw new RagAccessException("400", "创建请求标识过长");
        var rows = jdbc.queryForList("SELECT course_id FROM course_creation_request WHERE owner_id=? AND request_key=?", UserContext.getCurrentUserId(), key);
        return rows.isEmpty() ? null : ((Number) rows.getFirst().get("course_id")).intValue();
    }

    public void rememberCreation(String key, Integer courseId) {
        if (key != null && !key.isBlank()) jdbc.update("INSERT INTO course_creation_request(owner_id,request_key,course_id) VALUES(?,?,?)", UserContext.getCurrentUserId(), key, courseId);
    }

    public Map<String,Object> creationResult(Integer courseId) {
        permissions.requireCourseTeacher(courseId);
        return Map.of("id", courseId, "courseId", courseId, "knowledgeBaseId", ensureBase(courseId).get("id"));
    }

    public List<Map<String,Object>> documentAccess(Integer courseId) {
        String base = baseId(courseId);
        return jdbc.queryForList("SELECT d.id AS document_id,d.document_version AS version,COALESCE(g.read_excerpt,FALSE) AS read_excerpt,COALESCE(g.download_allowed,FALSE) AS download_allowed "
                + "FROM native_rag_document d LEFT JOIN native_rag_course_grant g ON g.document_id=d.id AND g.course_id=? AND g.document_version=d.document_version AND g.revoked_at IS NULL WHERE d.kb_id=?", courseId, base);
    }

    @Transactional public List<Map<String,Object>> updateDocumentAccess(Integer courseId, List<String> ids, boolean read, boolean download) {
        String base = baseId(courseId);
        if (ids == null || ids.isEmpty()) throw new RagAccessException("400", "请选择资料");
        for (String id : new LinkedHashSet<>(ids)) {
            var docs = jdbc.queryForList("SELECT * FROM native_rag_document WHERE id=? AND kb_id=? FOR UPDATE", id, base);
            if (docs.isEmpty()) throw new RagAccessException("403", "资料不属于当前课程");
            var doc = docs.getFirst();
            var grants = jdbc.queryForList("SELECT id FROM native_rag_course_grant WHERE course_id=? AND document_id=? AND document_version=?", courseId, id, doc.get("document_version"));
            if (grants.isEmpty()) jdbc.update("INSERT INTO native_rag_course_grant(id,owner_id,course_id,document_id,document_version,read_excerpt,download_allowed) VALUES(?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), UserContext.getCurrentUserId(), courseId, id, doc.get("document_version"), read, read && download);
            else jdbc.update("UPDATE native_rag_course_grant SET read_excerpt=?,download_allowed=?,revoked_at=NULL WHERE id=?", read, read && download, grants.getFirst().get("id"));
            if (!read) jdbc.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP WHERE course_id=? AND document_id=?", courseId, id);
        }
        return documentAccess(courseId);
    }

    public void archive(Integer courseId) {
        requireCourseManager(courseId);
        jdbc.update("UPDATE course_knowledge_base SET archived=TRUE WHERE course_id=?", courseId);
        jdbc.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP WHERE course_id=?", courseId);
    }
}
