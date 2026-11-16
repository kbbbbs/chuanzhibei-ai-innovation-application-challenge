package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.rag.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** A chapter has one source visibility scope. Publication projects an immutable reviewed snapshot. */
@Service
public class GraphService {
    public record CreateForm(Integer courseId, Integer chapterId, String title, String documentId,
                             int documentVersion, int pageStart, int pageEnd) { }
    public record DraftForm(int revision, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) { }
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RagAuthorizationService authorization;
    private final Neo4jGraphRepository graph;
    private final TransactionTemplate transaction;
    @Value("${rag.graph.worker-enabled:true}") private boolean workerEnabled = true;

    public GraphService(JdbcTemplate db, ObjectMapper json, RagAuthorizationService authorization, Neo4jGraphRepository graph) {
        this.db = db; this.json = json; this.authorization = authorization; this.graph = graph;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource()));
    }

    public Map<String, Object> create(CreateForm form) {
        if (form == null || form.documentVersion() < 1) throw invalid("请选择课程、章节与资料版本");
        RagActor actor = authorization.currentActor();
        requireTeacher(actor, form.courseId());
        requireChapter(form.courseId(), form.chapterId());
        String documentId = GraphSnapshotValidator.id(form.documentId());
        authorization.requireCourseDocumentRead(actor, form.courseId(), documentId, form.documentVersion(), false);
        Map<String, Object> document = currentDocument(documentId);
        if (number(document, "document_version") != form.documentVersion() || document.get("active_index_version") == null)
            throw conflict("请选择已解析完成的当前资料版本");
        int pages = number(document, "page_count");
        if (pages > 0 && (form.pageStart() < 1 || form.pageEnd() < form.pageStart() || form.pageEnd() > pages))
            throw invalid("章节页码范围无效");
        if (pages == 0 && (form.pageStart() != 0 || form.pageEnd() != 0)) throw invalid("文本资料请使用 0–0 表示全文");
        String id = UUID.randomUUID().toString();
        String title = GraphSnapshotValidator.text(form.title(), 200);
        transaction.executeWithoutResult(status -> {
            db.update("INSERT INTO native_graph_definition(id,course_id,chapter_id,owner_id,title,document_id,document_version,parse_revision,index_version,page_start,page_end) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    id, form.courseId(), form.chapterId(), actor.userId(), title, documentId, form.documentVersion(), number(document, "parse_revision"), document.get("active_index_version"), form.pageStart(), form.pageEnd());
            db.update("INSERT INTO native_graph_version(graph_id,version,snapshot_json,created_by) VALUES(?,1,?,?)",
                    id, encode(Map.of("nodes", List.of(), "edges", List.of())), actor.userId());
            authorized(id, actor);
        });
        return get(id, true);
    }

    public List<Map<String, Object>> list(Integer courseId) {
        RagActor actor = authorization.currentActor();
        if (courseId == null || courseId < 1) throw invalid("请选择课程");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : db.queryForList("SELECT * FROM native_graph_definition WHERE course_id=? ORDER BY created_at DESC", courseId)) {
            try {
                authorized(row, actor);
                if (isTeacher(actor, courseId) || row.get("published_version") != null) result.add(metadata(row));
            } catch (RagAccessException inaccessible) {
                if (!Set.of("403", "404", "409").contains(inaccessible.code())) throw inaccessible;
            }
        }
        result.removeIf(item -> {
            try { authorized(item.get("graphId").toString(), actor); return false; }
            catch (RagAccessException inaccessible) {
                if (!Set.of("403", "404", "409").contains(inaccessible.code())) throw inaccessible;
                return true;
            }
        });
        // Rechecking also rejects an account revoked while filtering the result.
        authorization.actorForUser(actor.userId());
        return List.copyOf(result);
    }

    public Map<String, Object> get(String id, boolean draft) {
        RagActor actor = authorization.currentActor();
        Map<String, Object> row = authorized(id, actor);
        boolean teacher = isTeacher(actor, number(row, "course_id"));
        if (draft) requireTeacher(actor, number(row, "course_id"));
        boolean useDraft = draft || (row.get("published_version") == null && teacher);
        int version = visibleVersion(row, useDraft);
        Map<String, Object> result = metadata(row);
        result.put("graphVersion", version);
        result.put("draft", useDraft);
        if (useDraft) result.putAll(snapshot(id, version));
        else {
            GraphSlice slice = graph.read(id, version, 100);
            result.put("nodes", slice.nodes()); result.put("edges", slice.edges()); result.put("truncated", slice.truncated());
        }
        result.putIfAbsent("truncated", false);
        result.put("evidence", selectedSources(row));
        authorized(row, actor);
        return result;
    }

    public Map<String, Object> sources(String id) {
        RagActor actor = authorization.currentActor();
        Map<String, Object> row = authorized(id, actor);
        requireTeacher(actor, number(row, "course_id"));
        List<Map<String, Object>> sources = selectedSources(row);
        authorized(row, actor);
        return Map.of("chunks", sources);
    }

    public String beginExtraction(String id, int revision, String model, String promptVersion, String fingerprint) {
        RagActor actor = authorization.currentActor();
        Map<String, Object> row = authorized(id, actor);
        requireTeacher(actor, number(row, "course_id"));
        if (number(row, "revision") != revision || "PROJECTING".equals(row.get("state"))) throw conflict("草稿已变化或正在发布");
        String taskId = UUID.randomUUID().toString();
        db.update("INSERT INTO native_graph_extraction(id,graph_id,input_revision,actor_id,model_name,prompt_version,source_fingerprint,status) VALUES(?,?,?,?,?,?,?,'PROCESSING')",
                taskId, id, revision, actor.userId(), GraphSnapshotValidator.text(model, 200), promptVersion, fingerprint);
        return taskId;
    }

    public void finishExtraction(String taskId, Integer outputRevision, String error) {
        db.update("UPDATE native_graph_extraction SET output_revision=?,status=?,error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PROCESSING'",
                outputRevision, error == null ? "DONE" : "FAILED", error, taskId);
    }

    public Map<String, Object> saveDraft(String id, DraftForm form) {
        if (form == null) throw invalid("草稿不能为空");
        RagActor actor = authorization.currentActor();
        Map<String, Object> row = authorized(id, actor);
        requireTeacher(actor, number(row, "course_id"));
        Map<String, Object> checked = GraphSnapshotValidator.validate(form.nodes(), form.edges(), sourceIds(row));
        transaction.executeWithoutResult(status -> {
            int changed = db.update("UPDATE native_graph_definition SET revision=revision+1,state='REVIEW_REQUIRED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND revision=? AND state<>'PROJECTING'", id, form.revision());
            if (changed != 1) throw conflict("草稿已更新或正在发布，请刷新后重试");
            db.update("INSERT INTO native_graph_version(graph_id,version,snapshot_json,created_by) VALUES(?,?,?,?)", id, form.revision() + 1, encode(checked), actor.userId());
            review(id, form.revision() + 1, actor.userId(), "SAVE_DRAFT");
            authorized(id, actor);
            requireTeacher(actor, number(row, "course_id"));
        });
        return get(id, true);
    }

    public Map<String, Object> publish(String id, int revision) {
        RagActor actor = authorization.currentActor();
        Map<String, Object> row = authorized(id, actor);
        requireTeacher(actor, number(row, "course_id"));
        if (number(row, "revision") != revision) throw conflict("草稿已更新，请重新审核");
        Map<String, Object> candidate = snapshot(id, revision);
        List<Map<String, Object>> nodes = facts(candidate, "nodes"), edges = facts(candidate, "edges");
        if (nodes.isEmpty()) throw invalid("请先整理至少一个有依据的知识实体");
        GraphSnapshotValidator.validate(nodes, edges, sourceIds(row));
        List<Map<String, Object>> existing = db.queryForList("SELECT * FROM native_graph_job WHERE graph_id=? AND version=?", id, revision);
        if (!existing.isEmpty()) {
            if ("FAILED".equals(existing.getFirst().get("status"))) return retry(id, existing.getFirst().get("id").toString());
            Map<String, Object> result = metadata(row);
            result.put("jobId", existing.getFirst().get("id"));
            return result;
        }
        String jobId = UUID.randomUUID().toString();
        List<Map<String, Object>> approvedEdges = new ArrayList<>();
        for (Map<String, Object> edge : edges) {
            Map<String, Object> approved = new LinkedHashMap<>(edge);
            approved.put("reviewStatus", "APPROVED");
            approved.put("reviewedBy", actor.userId());
            approved.put("reviewedAt", Instant.now().toString());
            approvedEdges.add(approved);
        }
        Map<String, Object> approved = Map.of("nodes", nodes, "edges", approvedEdges);
        transaction.executeWithoutResult(status -> {
            int changed = db.update("UPDATE native_graph_definition SET state='PROJECTING',updated_at=CURRENT_TIMESTAMP WHERE id=? AND revision=? AND state<>'PROJECTING'", id, revision);
            if (changed != 1) throw conflict("图谱正在发布或草稿已变化");
            authorized(id, actor);
            requireTeacher(actor, number(row, "course_id"));
            db.update("UPDATE native_graph_version SET snapshot_json=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE graph_id=? AND version=?", encode(approved), actor.userId(), id, revision);
            db.update("INSERT INTO native_graph_job(id,graph_id,version,actor_id,actor_role) VALUES(?,?,?,?,?)", jobId, id, revision, actor.userId(), actor.roleCode());
            review(id, revision, actor.userId(), "APPROVE_PUBLISH");
        });
        Map<String, Object> result = metadata(authorized(id, actor));
        result.put("jobId", jobId);
        return result;
    }

    public Map<String, Object> job(String id, String jobId) {
        RagActor actor = authorization.currentActor();
        Map<String, Object> row = authorized(id, actor);
        requireTeacher(actor, number(row, "course_id"));
        Map<String, Object> job = jobRow(id, jobId);
        return Map.of("jobId", jobId, "graphId", id, "graphVersion", number(job, "version"), "status", job.get("status"),
                "attempts", number(job, "attempts"), "error", Objects.toString(job.get("error_message"), ""));
    }

    public Map<String, Object> retry(String id, String jobId) {
        RagActor actor = authorization.currentActor();
        Map<String, Object> row = authorized(id, actor);
        requireTeacher(actor, number(row, "course_id"));
        Map<String, Object> job = jobRow(id, jobId);
        if (number(row, "revision") != number(job, "version")) throw conflict("草稿已经变化，请审核当前草稿并重新发布");
        transaction.executeWithoutResult(status -> {
            int changedGraph = db.update("UPDATE native_graph_definition SET state='PROJECTING',updated_at=CURRENT_TIMESTAMP WHERE id=? AND revision=? AND state<>'PROJECTING'", id, number(job, "version"));
            if (changedGraph != 1) throw conflict("草稿已变化或正在发布");
            int changed = db.update("UPDATE native_graph_job SET status='QUEUED',actor_id=?,actor_role=?,error_message=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='FAILED'", actor.userId(), actor.roleCode(), jobId);
            if (changed != 1) throw conflict("当前任务无需重试");
            authorized(id, actor);
            requireTeacher(actor, number(row, "course_id"));
            review(id, number(job, "version"), actor.userId(), "RETRY_PUBLISH");
        });
        return job(id, jobId);
    }

    @Scheduled(fixedDelayString = "${rag.graph.worker-delay-ms:3000}", initialDelayString = "${rag.graph.worker-initial-delay-ms:10000}")
    public void processPending() {
        if (!workerEnabled) return;
        List<Map<String, Object>> pending = db.queryForList("SELECT * FROM native_graph_job WHERE status='QUEUED' OR (status='PROCESSING' AND lease_until<CURRENT_TIMESTAMP) ORDER BY created_at LIMIT 5");
        for (Map<String, Object> job : pending) processJob(job);
    }

    private void processJob(Map<String, Object> job) {
        String jobId = job.get("id").toString(), id = job.get("graph_id").toString();
        int version = number(job, "version");
        String leaseToken = UUID.randomUUID().toString();
        int claimed = db.update("UPDATE native_graph_job SET status='PROCESSING',attempts=attempts+1,lease_until=?,lease_token=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND (status='QUEUED' OR (status='PROCESSING' AND lease_until<CURRENT_TIMESTAMP))",
                Timestamp.from(Instant.now().plusSeconds(300)), leaseToken, jobId);
        if (claimed != 1) return;
        try {
            RagActor actor = authorization.actorForUser(number(job, "actor_id"));
            if (!actor.roleCode().equals(job.get("actor_role"))) throw denied("任务执行身份已经变化");
            Map<String, Object> row = authorized(id, actor);
            requireTeacher(actor, number(row, "course_id"));
            if (number(row, "revision") != version) throw conflict("任务版本已过期");
            Map<String, Object> data = snapshot(id, version);
            GraphSnapshotValidator.validate(facts(data, "nodes"), facts(data, "edges"), sourceIds(row));
            graph.project(id, version, facts(data, "nodes"), facts(data, "edges"));
            transaction.executeWithoutResult(status -> {
                authorized(id, actor);
                requireTeacher(actor, number(row, "course_id"));
                int completed = db.update("UPDATE native_graph_job SET status='DONE',lease_until=NULL,error_message=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PROCESSING' AND lease_token=?", jobId, leaseToken);
                if (completed != 1) throw conflict("任务执行租约已更新");
                int changed = db.update("UPDATE native_graph_definition SET published_version=?,state='PUBLISHED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND revision=? AND state='PROJECTING'", version, id, version);
                if (changed != 1) throw conflict("发布版本发生变化，请重新审核");
            });
        } catch (Exception failure) {
            String message = failure instanceof RagAccessException ? failure.getMessage() : "图谱同步失败，可重试";
            int failed = db.update("UPDATE native_graph_job SET status='FAILED',lease_until=NULL,error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PROCESSING' AND lease_token=?", message, jobId, leaseToken);
            if (failed == 1) db.update("UPDATE native_graph_definition SET state='REVIEW_REQUIRED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND revision=? AND state='PROJECTING'", id, version);
        }
    }

    public Map<String, Object> evidence(String id, String evidenceId) {
        RagActor actor = authorization.currentActor();
        Map<String, Object> row = authorized(id, actor);
        int version = visibleVersion(row, row.get("published_version") == null && isTeacher(actor, number(row, "course_id")));
        Map<String, Object> data = snapshot(id, version);
        boolean referenced = facts(data, "nodes").stream().anyMatch(n -> ((List<?>)n.get("evidenceIds")).contains(evidenceId))
                || facts(data, "edges").stream().anyMatch(n -> ((List<?>)n.get("evidenceIds")).contains(evidenceId));
        // Teachers may inspect candidate evidence during editing.
        if (!referenced && !isTeacher(actor, number(row, "course_id"))) throw new RagAccessException("404", "证据不存在");
        Map<String, Object> source = selectedSources(row).stream().filter(s -> evidenceId.equals(s.get("id"))).findFirst()
                .orElseThrow(() -> new RagAccessException("404", "证据不存在"));
        authorized(row, actor);
        return source;
    }

    public List<Map<String, Object>> entities(String id, String keyword) {
        Map<String, Object> data = get(id, false);
        String query = Objects.toString(keyword, "").strip().toLowerCase(Locale.ROOT);
        if (query.length() > 100) throw invalid("搜索词过长");
        RagActor actor = authorization.currentActor();
        List<Map<String, Object>> result = facts(snapshot(id, ((Number)data.get("graphVersion")).intValue()), "nodes").stream()
                .filter(n -> n.get("name").toString().toLowerCase(Locale.ROOT).contains(query)
                        || ((List<?>) n.get("aliases")).stream().anyMatch(a -> a.toString().toLowerCase(Locale.ROOT).contains(query)))
                .limit(100).toList();
        authorized(id, actor);
        return result;
    }

    public Map<String, Object> neighbors(String id, String entityId, int depth, String relationType) {
        return querySlice(id, (version) -> graph.neighbors(id, version, entityId, depth, relationType));
    }

    public Map<String, Object> paths(String id, String from, String to, boolean learningOnly) {
        return querySlice(id, (version) -> graph.paths(id, version, from, to, learningOnly));
    }

    private Map<String, Object> querySlice(String id, java.util.function.IntFunction<GraphSlice> query) {
        RagActor actor = authorization.currentActor();
        Map<String, Object> row = authorized(id, actor);
        int version = visibleVersion(row, false);
        GraphSlice slice = query.apply(version);
        authorized(row, actor);
        return Map.of("graphId", id, "graphVersion", version, "nodes", slice.nodes(), "edges", slice.edges(), "truncated", slice.truncated());
    }

    private Map<String, Object> authorized(String id, RagActor actor) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM native_graph_definition WHERE id=?", GraphSnapshotValidator.id(id));
        if (rows.isEmpty()) throw new RagAccessException("404", "图谱不存在");
        Map<String, Object> row = rows.getFirst();
        authorized(row, actor);
        return row;
    }

    private void authorized(Map<String, Object> row, RagActor actor) {
        authorization.requireCourseDocumentRead(actor, number(row, "course_id"), row.get("document_id").toString(), number(row, "document_version"), false);
        requireChapter(number(row, "course_id"), number(row, "chapter_id"));
        Map<String, Object> document = currentDocument(row.get("document_id").toString());
        if (number(document, "document_version") != number(row, "document_version")
                || number(document, "parse_revision") != number(row, "parse_revision")
                || !Objects.equals(document.get("active_index_version"), row.get("index_version")))
            throw conflict("资料已更新，请根据当前资料重新建立图谱");
    }

    private void requireTeacher(RagActor actor, Integer courseId) {
        RagActor active = authorization.actorForUser(actor.userId());
        if (!isTeacher(active, courseId)) throw denied("只有本课程教师可以维护图谱");
    }

    private boolean isTeacher(RagActor actor, Integer courseId) {
        if (!"teacher".equals(actor.roleCode()) || courseId == null) return false;
        return db.queryForObject("SELECT COUNT(*) FROM t_course WHERE id=? AND teacher_id=?", Integer.class, courseId, actor.userId()) > 0;
    }

    private void requireChapter(Integer courseId, Integer chapterId) {
        if (courseId == null || chapterId == null || db.queryForObject("SELECT COUNT(*) FROM t_chapter WHERE id=? AND course_id=? AND status=1", Integer.class, chapterId, courseId) == 0)
            throw invalid("章节不属于所选课程");
    }

    private Map<String, Object> currentDocument(String id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM native_rag_document WHERE id=?", id);
        if (rows.isEmpty()) throw new RagAccessException("404", "资料不存在或已删除");
        return rows.getFirst();
    }

    private List<Map<String, Object>> selectedSources(Map<String, Object> row) {
        String pageFilter = number(row, "page_start") == 0 ? "" : " AND page_number BETWEEN ? AND ?";
        List<Object> args = new ArrayList<>(List.of(row.get("document_id"), row.get("document_version"), row.get("parse_revision"), row.get("index_version")));
        if (number(row, "page_start") > 0) { args.add(row.get("page_start")); args.add(row.get("page_end")); }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> chunk : db.queryForList("SELECT * FROM native_rag_chunk WHERE document_id=? AND document_version=? AND parse_revision=? AND index_version=?" + pageFilter + " ORDER BY ordinal_number", args.toArray())) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("id", chunk.get("id")); source.put("chunkId", chunk.get("id")); source.put("content", chunk.get("content"));
            source.put("pageNo", chunk.get("page_number")); source.put("documentId", row.get("document_id"));
            source.put("documentVersion", row.get("document_version")); source.put("parseRevision", row.get("parse_revision"));
            source.put("indexVersion", row.get("index_version")); source.put("charStart", chunk.get("char_start")); source.put("charEnd", chunk.get("char_end"));
            result.add(source);
        }
        return List.copyOf(result);
    }

    private Set<String> sourceIds(Map<String, Object> row) {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> source : selectedSources(row)) ids.add(source.get("id").toString());
        return ids;
    }

    private Map<String, Object> snapshot(String id, int version) {
        List<String> rows = db.queryForList("SELECT snapshot_json FROM native_graph_version WHERE graph_id=? AND version=?", String.class, id, version);
        if (rows.isEmpty()) throw conflict("图谱版本不可用");
        try { return json.readValue(rows.getFirst(), new TypeReference<>() { }); }
        catch (Exception failure) { throw conflict("图谱版本无法读取"); }
    }

    private Map<String, Object> jobRow(String id, String jobId) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM native_graph_job WHERE graph_id=? AND id=?", id, GraphSnapshotValidator.id(jobId));
        if (rows.isEmpty()) throw new RagAccessException("404", "图谱任务不存在");
        return rows.getFirst();
    }

    private Map<String, Object> metadata(Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("graphId", row.get("id")); data.put("courseId", row.get("course_id")); data.put("chapterId", row.get("chapter_id"));
        data.put("title", row.get("title")); data.put("revision", row.get("revision")); data.put("publishedVersion", row.get("published_version"));
        data.put("status", row.get("state")); data.put("documentId", row.get("document_id")); data.put("documentVersion", row.get("document_version"));
        data.put("pageStart", row.get("page_start")); data.put("pageEnd", row.get("page_end"));
        return data;
    }

    private int visibleVersion(Map<String, Object> row, boolean draft) {
        if (draft) return number(row, "revision");
        if (row.get("published_version") == null) throw new RagAccessException("404", "本章图谱尚未发布");
        return number(row, "published_version");
    }

    private void review(String id, int version, int actor, String action) {
        db.update("INSERT INTO native_graph_review(id,graph_id,version,actor_id,action) VALUES(?,?,?,?,?)", UUID.randomUUID().toString(), id, version, actor, action);
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception failure) { throw invalid("图谱数据格式无效"); }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> facts(Map<String, Object> data, String key) { return (List<Map<String, Object>>) data.get(key); }
    static int number(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    static RagAccessException invalid(String message) { return new RagAccessException("400", message); }
    static RagAccessException conflict(String message) { return new RagAccessException("409", message); }
    static RagAccessException denied(String message) { return new RagAccessException("403", message); }
}
