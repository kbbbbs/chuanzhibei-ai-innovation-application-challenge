package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.Files;
import java.util.*;

@Service
public class LegacyCourseLibraryService {
    private final JdbcTemplate jdbc;
    private final CourseKnowledgeService courses;
    private final ObjectMapper json;
    private final RagFileStore files;
    public LegacyCourseLibraryService(JdbcTemplate jdbc, CourseKnowledgeService courses, ObjectMapper json, RagFileStore files) {
        this.jdbc = jdbc; this.courses = courses; this.json = json; this.files = files;
    }

    public List<Map<String,Object>> list() {
        int user = writer();
        var rows = jdbc.queryForList("SELECT b.*,(SELECT COUNT(*) FROM native_rag_document d WHERE d.kb_id=b.id) AS documentCount "
                + "FROM native_rag_base b LEFT JOIN course_knowledge_base c ON c.kb_id=b.id LEFT JOIN course_library_assignment a ON a.legacy_kb_id=b.id "
                + "WHERE b.owner_id=? AND c.kb_id IS NULL AND a.legacy_kb_id IS NULL ORDER BY b.created_at", user);
        for (var base : rows) {
            var grants = jdbc.queryForList("SELECT DISTINCT g.course_id FROM native_rag_course_grant g JOIN native_rag_document d ON d.id=g.document_id WHERE d.kb_id=? AND g.revoked_at IS NULL", base.get("id"));
            base.put("relatedCourseIds", grants.stream().map(g -> g.get("course_id")).toList());
            var suggestions = jdbc.queryForList("SELECT published_course_id FROM t_ai_preparation WHERE owned_knowledge_base_id=? AND published_course_id IS NOT NULL", base.get("id"));
            if (suggestions.size() == 1) base.put("suggestedCourseId", suggestions.getFirst().get("published_course_id"));
        }
        return rows;
    }

    @Transactional public Map<String,Object> assign(String legacy, Integer courseId, String title) {
        int user = writer();
        var base = jdbc.queryForMap("SELECT * FROM native_rag_base WHERE id=? FOR UPDATE", legacy);
        if (((Number)base.get("owner_id")).intValue() != user) throw new RagAccessException("403", "只能整理自己的资料库");
        var assigned = jdbc.queryForList("SELECT course_id FROM course_library_assignment WHERE legacy_kb_id=?", legacy);
        if (!assigned.isEmpty()) return Map.of("courseId", assigned.getFirst().get("course_id"), "assigned", true);
        if (courses.courseForBase(legacy) != null) throw new RagAccessException("409", "该资料库已有课程归属");
        if (courseId == null) courseId = createDraft(title);
        courses.requireCourseManager(courseId);
        String target = courses.baseId(courseId);
        var docs = jdbc.queryForList("SELECT * FROM native_rag_document WHERE kb_id=? FOR UPDATE", legacy);
        var questionTasks = questionTasks(legacy);
        if (questionTasks.stream().anyMatch(task -> "RUNNING".equals(task.get("status")))) throw new RagAccessException("409", "出题任务正在运行，请完成后再整理归属");
        if (docs.stream().anyMatch(this::hasActiveProcessing)) throw new RagAccessException("409", "资料正在处理，请完成后再整理归属");
        Map<String,Object> snapshot = new LinkedHashMap<>();
        snapshot.put("base", base); snapshot.put("documents", docs);
        snapshot.put("questionTasks", questionTasks);
        snapshot.put("grants", jdbc.queryForList("SELECT g.* FROM native_rag_course_grant g JOIN native_rag_document d ON d.id=g.document_id WHERE d.kb_id=?", legacy));
        snapshot.put("sessions", jdbc.queryForList("SELECT id,kb_ids,course_id,mode FROM t_native_rag_session WHERE owner_id=?", user).stream().filter(session -> readIds(String.valueOf(session.get("kb_ids"))).contains(legacy)).toList());
        snapshot.put("projects", jdbc.queryForList("SELECT id,config_json,owned_knowledge_base_id,knowledge_base_ids_json FROM t_ai_preparation WHERE owned_knowledge_base_id=?", legacy));
        var copies = new ArrayList<String>();
        jdbc.update("INSERT INTO course_library_assignment(legacy_kb_id,course_id,snapshot_json) VALUES(?,?,?)", legacy, courseId, write(snapshot));
        for (var doc : docs) {
            String id = doc.get("id").toString();
            var activeGrants = jdbc.queryForList("SELECT * FROM native_rag_course_grant WHERE document_id=? AND revoked_at IS NULL", id);
            var courseCopies = new HashMap<Integer,String>();
            for (var task : questionTasks) {
                int taskCourse = ((Number)task.get("course_id")).intValue();
                if (taskCourse == courseId || courseCopies.containsKey(taskCourse)) continue;
                String copy = cloneDocument(doc,ensureGrantedCourseBase(taskCourse));
                courseCopies.put(taskCourse,copy); copies.add(copy);
            }
            for (var grant : activeGrants) {
                int other = ((Number)grant.get("course_id")).intValue();
                if (other == courseId) continue;
                // A pre-existing grant is an explicit source of the migration mapping.
                String otherBase = ensureGrantedCourseBase(other);
                String copy = courseCopies.get(other);
                if (copy == null) {
                    copy = cloneDocument(doc, otherBase);
                    courseCopies.put(other,copy); copies.add(copy);
                }
                jdbc.update("UPDATE native_rag_course_grant SET document_id=? WHERE id=?", copy, grant.get("id"));
            }
            jdbc.update("UPDATE native_rag_document SET kb_id=?,migration_identity=? WHERE id=?", target, id, id);
            jdbc.update("UPDATE native_rag_chunk SET kb_id=? WHERE document_id=?", target, id);
        }
        for (var task : questionTasks) {
            int taskCourse = ((Number)task.get("course_id")).intValue();
            String taskBase = taskCourse == courseId ? target : ensureGrantedCourseBase(taskCourse);
            Map<String,Object> request;
            try { request = json.readValue(task.get("request_json").toString(),new TypeReference<>() {}); }
            catch (Exception error) { throw new RagAccessException("409", "出题草稿配置无法读取"); }
            request.put("knowledgeBaseId",taskBase);
            jdbc.update("UPDATE t_question_generation_task SET kb_id=?,request_json=? WHERE id=?",taskBase,write(request),task.get("id"));
        }
        bindHistoricalProjects(legacy, courseId, target);
        for (var session : jdbc.queryForList("SELECT * FROM t_native_rag_session WHERE owner_id=?", user)) {
            List<String> ids = readIds(String.valueOf(session.get("kb_ids")));
            if (!ids.contains(legacy)) continue;
            if (ids.size() == 1) jdbc.update("UPDATE t_native_rag_session SET kb_ids=?,course_id=? WHERE id=?", write(List.of("course:" + courseId)), courseId, session.get("id"));
            else jdbc.update("UPDATE t_native_rag_session SET mode='HISTORY' WHERE id=?", session.get("id"));
        }
        snapshot.put("copies", copies);
        var affected = new ArrayList<String>(copies);
        docs.forEach(doc -> affected.add(doc.get("id").toString()));
        snapshot.put("affected", affected);
        snapshot.put("afterFingerprint", fingerprint(affected));
        snapshot.put("afterProjects", currentRows("t_ai_preparation", snapshot.get("projects")));
        snapshot.put("afterTasks", currentRows("t_question_generation_task", questionTasks));
        jdbc.update("UPDATE course_library_assignment SET snapshot_json=? WHERE legacy_kb_id=?", write(snapshot), legacy);
        return Map.of("courseId", courseId, "knowledgeBaseId", target, "documentCount", docs.size(), "assigned", true);
    }

    public Map<String,Object> preview(String legacy, Integer courseId) {
        int user = writer();
        var base = jdbc.queryForMap("SELECT * FROM native_rag_base WHERE id=?", legacy);
        if (((Number)base.get("owner_id")).intValue() != user) throw new RagAccessException("403", "只能整理自己的资料库");
        if (courseId != null) courses.requireCourseManager(courseId);
        var documents = jdbc.queryForList("SELECT id,file_name,document_version FROM native_rag_document WHERE kb_id=? ORDER BY id", legacy);
        var grants = jdbc.queryForList("SELECT g.course_id,g.document_id,g.document_version,g.read_excerpt,g.download_allowed FROM native_rag_course_grant g JOIN native_rag_document d ON d.id=g.document_id WHERE d.kb_id=? AND g.revoked_at IS NULL", legacy);
        return Map.of("knowledgeBaseId", legacy, "documents", documents, "existingGrants", grants, "copiesRequired", grants.stream().filter(g -> !Objects.equals(courseId, ((Number)g.get("course_id")).intValue())).count());
    }

    @Transactional public Map<String,Object> rollback(String legacy) {
        int user = writer();
        var base = jdbc.queryForMap("SELECT * FROM native_rag_base WHERE id=? FOR UPDATE", legacy);
        if (((Number)base.get("owner_id")).intValue() != user) throw new RagAccessException("403", "只能恢复自己的资料归属");
        var rows = jdbc.queryForList("SELECT * FROM course_library_assignment WHERE legacy_kb_id=? FOR UPDATE", legacy);
        if (rows.isEmpty()) return Map.of("restored", true);
        Map<String,Object> snapshot;
        try { snapshot = json.readValue(rows.getFirst().get("snapshot_json").toString(), new TypeReference<>() {}); }
        catch (Exception e) { throw new RagAccessException("409", "归属备份无法读取"); }
        if (!write(snapshot.get("afterProjects")).equals(write(currentRows("t_ai_preparation", snapshot.get("projects"))))
                || !write(snapshot.get("afterTasks")).equals(write(currentRows("t_question_generation_task", snapshot.getOrDefault("questionTasks",List.of())))))
            throw new RagAccessException("409", "归属后草稿已有修改，请核对后恢复");
        List<String> affected = json.convertValue(snapshot.get("affected"), new TypeReference<>() {});
        if (!Objects.equals(snapshot.get("afterFingerprint"), fingerprint(affected))) throw new RagAccessException("409", "归属后资料已有变化，请核对后恢复");
        List<Map<String,Object>> documents = json.convertValue(snapshot.get("documents"), new TypeReference<>() {});
        for (var document : documents) {
            String id = Objects.toString(document.getOrDefault("id", document.get("ID")));
            Object identity = document.getOrDefault("migration_identity", document.getOrDefault("MIGRATION_IDENTITY", ""));
            jdbc.update("UPDATE native_rag_document SET kb_id=?,migration_identity=? WHERE id=?", legacy, identity, id);
            jdbc.update("UPDATE native_rag_chunk SET kb_id=? WHERE document_id=?", legacy, id);
        }
        restoreRows("native_rag_course_grant", snapshot.get("grants"));
        restoreRows("t_native_rag_session", snapshot.get("sessions"));
        restoreRows("t_ai_preparation", snapshot.get("projects"));
        restoreRows("t_question_generation_task", snapshot.getOrDefault("questionTasks", List.of()));
        List<String> copies = json.convertValue(snapshot.get("copies"), new TypeReference<>() {});
        for (String copy : copies) {
            var keys = jdbc.queryForList("SELECT DISTINCT file_key FROM native_rag_document_version WHERE document_id=?", String.class, copy);
            jdbc.update("DELETE FROM native_rag_document WHERE id=?", copy);
            for (String key : keys) jdbc.update("INSERT INTO native_rag_file_cleanup(file_key) VALUES(?)", key);
        }
        jdbc.update("DELETE FROM course_library_assignment WHERE legacy_kb_id=?", legacy);
        return Map.of("restored", true);
    }

    private List<Map<String,Object>> currentRows(String table, Object previous) {
        List<Map<String,Object>> rows = json.convertValue(previous,new TypeReference<>() {});
        var current = new ArrayList<Map<String,Object>>();
        for (var row : rows) {
            Object id = row.getOrDefault("id",row.get("ID"));
            current.add(jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=?",id));
        }
        return current;
    }
    private void restoreRows(String table, Object value) {
        List<Map<String,Object>> rows = json.convertValue(value, new TypeReference<>() {});
        for (var row : rows) {
            var normalized = new LinkedHashMap<String,Object>(); row.forEach((key,item) -> normalized.put(key.toLowerCase(Locale.ROOT), item));
            Object id = normalized.remove("id");
            normalized.replaceAll((key,item) -> key.endsWith("_at") && item instanceof Number number ? new java.sql.Timestamp(number.longValue()) : item);
            jdbc.update("UPDATE " + table + " SET " + String.join(",", normalized.keySet().stream().map(key -> key + "=?").toList()) + " WHERE id=?", java.util.stream.Stream.concat(normalized.values().stream(), java.util.stream.Stream.of(id)).toArray());
        }
    }
    private String fingerprint(List<String> ids) {
        var state = new LinkedHashMap<String,Object>();
        for (String id : ids) for (String table : List.of("native_rag_document", "native_rag_document_version", "native_rag_index_version", "native_rag_page", "native_rag_chunk", "native_rag_visual", "native_rag_visual_page", "native_rag_page_edit", "native_rag_course_grant")) {
            String column = table.equals("native_rag_document") ? "id" : "document_id";
            state.put(id + table, jdbc.queryForList("SELECT * FROM " + table + " WHERE " + column + "=?", id));
        }
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(write(state).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private boolean hasActiveProcessing(Map<String,Object> document) {
        Object jobId = document.get("processing_job_id");
        if (jobId != null) {
            var jobs = jdbc.queryForList("SELECT stage FROM native_rag_ingestion_job WHERE id=? AND document_id=?",jobId,document.get("id"));
            if (!jobs.isEmpty()) return !Set.of("COMPLETED","FAILED","CANCELLED").contains(jobs.getFirst().get("stage"));
        }
        // Legacy rows can retain a processing version after their task has finished.
        return Set.of("QUEUED","EXTRACTING","OCR","CHUNKING","INDEXING","PROCESSING").contains(document.get("processing_state"));
    }

    private int createDraft(String title) {
        if (title == null || title.isBlank() || title.length() > 100) throw new RagAccessException("400", "请填写新课程名称");
        var categories = jdbc.queryForList("SELECT id FROM t_course_category ORDER BY id LIMIT 1");
        if (categories.isEmpty()) throw new RagAccessException("409", "请先创建课程分类");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO t_course(title,description,teacher_id,category_id,status,created_at,updated_at) VALUES(?,?,?,?,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", new String[]{"id"});
            statement.setString(1, title.trim()); statement.setString(2, ""); statement.setInt(3, writer()); statement.setObject(4, categories.getFirst().get("id")); return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).intValue();
    }

    private String ensureGrantedCourseBase(int id) {
        jdbc.queryForMap("SELECT id FROM t_course WHERE id=? FOR UPDATE", id);
        var link = jdbc.queryForList("SELECT kb_id FROM course_knowledge_base WHERE course_id=?", id);
        if (!link.isEmpty()) return link.getFirst().get("kb_id").toString();
        var course = jdbc.queryForMap("SELECT * FROM t_course WHERE id=?", id);
        String kb = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO native_rag_base(id,owner_id,name,description,shared) VALUES(?,?,?,?,FALSE)", kb, course.get("teacher_id"), course.get("title"), "课程专属资料");
        jdbc.update("INSERT INTO course_knowledge_base(course_id,kb_id) VALUES(?,?)", id, kb);
        return kb;
    }

    private String cloneDocument(Map<String,Object> original, String target) {
        String old = original.get("id").toString();
        Map<String,String> ids = new LinkedHashMap<>();
        ids.put(old, UUID.randomUUID().toString()); ids.put(original.get("kb_id").toString(), target);
        var tables = List.of("native_rag_document_version", "native_rag_index_version", "native_rag_page", "native_rag_chunk", "native_rag_visual", "native_rag_visual_page", "native_rag_page_edit");
        Map<String,List<Map<String,Object>>> children = new LinkedHashMap<>();
        for (String table : tables) {
            var rows = jdbc.queryForList("SELECT * FROM " + table + " WHERE document_id=?", old);
            children.put(table, rows);
            if (!table.equals("native_rag_page_edit")) for (var row : rows) if (row.get("id") != null) ids.put(row.get("id").toString(), UUID.randomUUID().toString());
        }
        Set<String> keys = new LinkedHashSet<>(); keys.add(original.get("file_key").toString());
        for (var row : children.get("native_rag_document_version")) keys.add(row.get("file_key").toString());
        for (String key : keys) {
            String copy = UUID.randomUUID().toString();
            try { files.store(copy, Files.readAllBytes(files.path(key))); }
            catch (Exception failure) { throw new RagAccessException("500", "复制历史原件失败，归属未更新"); }
            ids.put(key, copy);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) files.delete(copy); }
            });
        }
        var document = remap(original, ids); document.put("migration_identity", ids.get(old)); document.put("processing_job_id", null); document.put("processing_version", null);
        insert("native_rag_document", document);
        for (var entry : children.entrySet()) for (var row : entry.getValue()) {
            var copy = remap(row, ids);
            if (entry.getKey().equals("native_rag_page_edit")) copy.remove("id");
            insert(entry.getKey(), copy);
        }
        return ids.get(old);
    }

    private Map<String,Object> remap(Map<String,Object> row, Map<String,String> ids) {
        var copy = new LinkedHashMap<String,Object>();
        row.forEach((key,value) -> {
            String column = key.toLowerCase(Locale.ROOT);
            Object mapped = value;
            if (value instanceof String text) {
                if (column.equals("id") || column.endsWith("_id") || column.equals("file_key") || column.endsWith("index_version")) mapped = ids.getOrDefault(text,text);
                else if (column.endsWith("_json")) {
                    try { mapped = write(remapReferences(json.readValue(text,Object.class),ids)); }
                    catch (Exception invalid) { throw new RagAccessException("409", "历史结构化资料无法复制，请修复后重试"); }
                }
            }
            copy.put(column,mapped);
        });
        return copy;
    }
    private Object remapReferences(Object value, Map<String,String> ids) {
        if (value instanceof List<?> list) return list.stream().map(item -> remapReferences(item,ids)).toList();
        if (value instanceof Map<?,?> map) {
            var result = new LinkedHashMap<String,Object>();
            map.forEach((key,item) -> {
                String name = key.toString();
                if (List.of("text","rawText","ocrText","ocrRawText","content","latex","chosenText").contains(name)) result.put(name,item);
                else result.put(name,remapReferences(item,ids));
            });
            return result;
        }
        return value instanceof String text ? ids.getOrDefault(text,text) : value;
    }
    private void insert(String table, Map<String,Object> values) {
        jdbc.update("INSERT INTO " + table + "(" + String.join(",", values.keySet()) + ") VALUES(" + String.join(",", Collections.nCopies(values.size(), "?")) + ")", values.values().toArray());
    }
    private void bindHistoricalProjects(String legacy, int course, String target) {
        for (var project : jdbc.queryForList("SELECT id,config_json FROM t_ai_preparation WHERE owned_knowledge_base_id=?", legacy)) {
            try {
                Map<String,Object> config = json.readValue(project.get("config_json").toString(), new TypeReference<>() {});
                config.put("targetCourseId", course); config.put("knowledgeBaseIds", List.of(target));
                jdbc.update("UPDATE t_ai_preparation SET owned_knowledge_base_id=?,config_json=? WHERE id=?", target, write(config), project.get("id"));
            } catch (Exception failure) { throw new RagAccessException("409", "历史备课配置无法迁移，请保留并修复后重试"); }
        }
    }
    private List<Map<String,Object>> questionTasks(String base) {
        try (var connection = jdbc.getDataSource().getConnection()) {
            String table = connection.getMetaData().storesUpperCaseIdentifiers() ? "T_QUESTION_GENERATION_TASK" : "t_question_generation_task";
            try (var rows = connection.getMetaData().getTables(connection.getCatalog(),null,table,new String[]{"TABLE"})) {
                if (!rows.next()) return List.of();
            }
        } catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
        return jdbc.queryForList("SELECT * FROM t_question_generation_task WHERE kb_id=?",base);
    }
    private int writer() {
        var actor = UserContext.get();
        if (actor == null || !List.of("teacher", "admin").contains(actor.getRoleCode())) throw new RagAccessException("403", "只有教师可整理资料");
        return actor.getUserId();
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private List<String> readIds(String value) { try { return json.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new RagAccessException("409", "历史会话范围无法读取"); } }
}
