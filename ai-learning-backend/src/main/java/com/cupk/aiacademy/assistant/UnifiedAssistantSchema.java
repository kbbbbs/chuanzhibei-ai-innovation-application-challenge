package com.cupk.aiacademy.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.util.*;

/** Additive schema and one-time legacy mapping. Original rows remain available for recovery. */
@Component
public class UnifiedAssistantSchema {
    private final JdbcTemplate db;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public UnifiedAssistantSchema(JdbcTemplate db) {
        this.db = db;
    }

    @PostConstruct
    public void initialize() {
        db.execute(
                "CREATE TABLE IF NOT EXISTS t_agent_session(id INT AUTO_INCREMENT PRIMARY"
                        + " KEY,agent_id INT NOT NULL DEFAULT 1,user_id INT NOT NULL,title"
                        + " VARCHAR(200),status INT DEFAULT 1,create_time TIMESTAMP DEFAULT"
                        + " CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute(
                "CREATE TABLE IF NOT EXISTS t_agent_message(id INT AUTO_INCREMENT PRIMARY"
                    + " KEY,session_id INT NOT NULL,role VARCHAR(20),content MEDIUMTEXT,tool_name"
                    + " VARCHAR(100),tool_args MEDIUMTEXT,tool_result MEDIUMTEXT,create_time"
                    + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        addColumn("t_agent_session", "assistant_scope", "VARCHAR(20)");
        addColumn("t_agent_session", "assistant_role", "VARCHAR(20)");
        addColumn("t_agent_session", "course_id", "INT");
        addColumn("t_agent_session", "lesson_id", "INT");
        addColumn("t_agent_session", "library_id", "VARCHAR(100)");
        addColumn("t_agent_message", "assistant_payload", "MEDIUMTEXT");
        db.execute(
                "CREATE TABLE IF NOT EXISTS assistant_request(request_id VARCHAR(80) NOT"
                    + " NULL,session_id INT NOT NULL,owner_id INT NOT NULL,status VARCHAR(20) NOT"
                    + " NULL,input_json MEDIUMTEXT,result_json MEDIUMTEXT,error_text"
                    + " VARCHAR(500),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at"
                    + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(session_id,request_id))");
        addColumn("assistant_request", "events_json", "MEDIUMTEXT");
        addColumn("assistant_request", "events_scope_json", "MEDIUMTEXT");
        db.execute(
                "CREATE TABLE IF NOT EXISTS assistant_legacy_session(legacy_id VARCHAR(100) PRIMARY"
                        + " KEY,session_id INT NOT NULL,snapshot_json MEDIUMTEXT,classification"
                        + " VARCHAR(20))");
        db.execute(
                "CREATE TABLE IF NOT EXISTS assistant_migration_backup(source_kind"
                    + " VARCHAR(30),source_id VARCHAR(100),snapshot_json MEDIUMTEXT,created_at"
                    + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(source_kind,source_id))");
        db.update(
                "UPDATE assistant_request SET"
                    + " status='INTERRUPTED',result_json=NULL,error_text='服务已重启，请重试本次提问',updated_at=CURRENT_TIMESTAMP"
                    + " WHERE status IN ('RUNNING','QUEUED')");
        for (var row :
                db.queryForList("SELECT * FROM t_agent_session WHERE assistant_scope IS NULL")) {
            backup("AGENT_SESSION", row.get("id").toString(), row);
            for (var message :
                    db.queryForList(
                            "SELECT * FROM t_agent_message WHERE session_id=?", row.get("id")))
                backup("AGENT_MESSAGE", message.get("id").toString(), message);
            db.update(
                    "UPDATE t_agent_session SET assistant_scope='HISTORY' WHERE id=? AND"
                            + " assistant_scope IS NULL",
                    row.get("id"));
        }
        if (!tableExists("t_native_rag_session") || !tableExists("t_native_rag_message")) return;
        for (var row :
                db.queryForList("SELECT * FROM t_native_rag_session ORDER BY created_at,id")) {
            String oldId = Objects.toString(row.get("id"));
            if (!db.queryForList(
                            "SELECT legacy_id FROM assistant_legacy_session WHERE legacy_id=?",
                            oldId)
                    .isEmpty()) continue;
            new org.springframework.transaction.support.TransactionTemplate(
                            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                                    Objects.requireNonNull(db.getDataSource())))
                    .executeWithoutResult(status -> migrate(row, oldId));
        }
    }

    private void migrate(Map<String, Object> row, String oldId) {
        backup("RAG_SESSION", oldId, row);
        List<String> scope;
        try {
            scope =
                    json.readValue(
                            Objects.toString(row.get("kb_ids"), "[]"),
                            json.getTypeFactory()
                                    .constructCollectionType(List.class, String.class));
        } catch (Exception bad) {
            scope = List.of();
        }
        Integer course = null, lesson = null;
        var courses = new LinkedHashSet<String>();
        var lessons = new LinkedHashSet<String>();
        boolean unknown = false;
        for (String part : scope) {
            if (part.matches("course:[1-9][0-9]*")) courses.add(part.substring(7));
            else if (part.matches("lesson:[1-9][0-9]*")) lessons.add(part.substring(7));
            else unknown = true;
        }
        if (!unknown && courses.size() == 1 && lessons.size() <= 1) {
            try {
                course = Integer.valueOf(courses.iterator().next());
                lesson = lessons.isEmpty() ? null : Integer.valueOf(lessons.iterator().next());
            } catch (NumberFormatException invalid) {
                course = null;
                lesson = null;
            }
        }
        String classification = course == null ? "HISTORY" : lesson == null ? "COURSE" : "LESSON";
        var keys = new GeneratedKeyHolder();
        Integer courseId = course, lessonId = lesson;
        db.update(
                connection -> {
                    var ps =
                            connection.prepareStatement(
                                    "INSERT INTO"
                                        + " t_agent_session(agent_id,user_id,title,status,create_time,update_time,assistant_scope,course_id,lesson_id)"
                                        + " VALUES(1,?,?,1,?,?,?,?,?)",
                                    new String[] {"id"});
                    ps.setObject(1, row.get("owner_id"));
                    ps.setObject(
                            2,
                            Objects.toString(row.get("title"), "历史对话")
                                    .substring(
                                            0,
                                            Math.min(
                                                    100,
                                                    Objects.toString(row.get("title"), "历史对话")
                                                            .length())));
                    ps.setObject(3, row.get("created_at"));
                    ps.setObject(4, row.get("updated_at"));
                    ps.setString(5, classification);
                    ps.setObject(6, courseId);
                    ps.setObject(7, lessonId);
                    return ps;
                },
                keys);
        int id = Objects.requireNonNull(keys.getKey()).intValue();
        for (var message :
                db.queryForList(
                        "SELECT * FROM t_native_rag_message WHERE session_id=? ORDER BY"
                                + " sequence_no",
                        oldId)) {
            backup("RAG_MESSAGE", Objects.toString(message.get("id")), message);
            String payload = Objects.toString(message.get("evidence_json"), null);
            db.update(
                    "INSERT INTO"
                        + " t_agent_message(session_id,role,content,assistant_payload,create_time)"
                        + " VALUES(?,?,?,?,?)",
                    id,
                    message.get("role"),
                    message.get("content"),
                    payload,
                    message.get("created_at"));
        }
        db.update(
                "INSERT INTO"
                    + " assistant_legacy_session(legacy_id,session_id,snapshot_json,classification)"
                    + " VALUES(?,?,?,?)",
                oldId,
                id,
                write(row),
                classification);
    }

    private void backup(String kind, String id, Map<String, Object> row) {
        if (db.queryForList(
                        "SELECT source_id FROM assistant_migration_backup WHERE source_kind=? AND"
                                + " source_id=?",
                        kind,
                        id)
                .isEmpty())
            db.update(
                    "INSERT INTO assistant_migration_backup(source_kind,source_id,snapshot_json)"
                            + " VALUES(?,?,?)",
                    kind,
                    id,
                    write(row));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("历史备份失败", e);
        }
    }

    private boolean tableExists(String table) {
        try (var connection = Objects.requireNonNull(db.getDataSource()).getConnection();
                var rs =
                        connection
                                .getMetaData()
                                .getTables(null, null, null, new String[] {"TABLE"})) {
            while (rs.next()) if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) return true;
            return false;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void addColumn(String table, String column, String type) {
        try (var connection = Objects.requireNonNull(db.getDataSource()).getConnection();
                var rs = connection.getMetaData().getColumns(null, null, null, null)) {
            while (rs.next())
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) return;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        db.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }
}
