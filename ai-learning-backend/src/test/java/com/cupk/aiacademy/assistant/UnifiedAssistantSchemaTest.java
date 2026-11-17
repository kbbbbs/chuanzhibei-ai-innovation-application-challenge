package com.cupk.aiacademy.assistant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

class UnifiedAssistantSchemaTest {
    @Test
    void restartInterruptsRequestsAndMigrationPreservesScopeAndOriginalRows() {
        var db =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                "jdbc:h2:mem:"
                                        + UUID.randomUUID()
                                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                                "sa",
                                ""));
        db.execute(
                "CREATE TABLE t_native_rag_session(id VARCHAR(36) PRIMARY KEY,owner_id INT,title"
                        + " VARCHAR(200),kb_ids TEXT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        db.execute(
                "CREATE TABLE t_native_rag_message(id VARCHAR(36) PRIMARY KEY,session_id"
                    + " VARCHAR(36),sequence_no BIGINT,role VARCHAR(16),content TEXT,evidence_json"
                    + " TEXT,created_at TIMESTAMP)");
        db.update(
                "INSERT INTO t_native_rag_session"
                    + " VALUES('old',7,'课时问答','[\"course:129\",\"lesson:5\"]',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update(
                "INSERT INTO t_native_rag_message"
                        + " VALUES('m1','old',1,'user','解释梯度',NULL,CURRENT_TIMESTAMP)");
        var schema = new UnifiedAssistantSchema(db);
        schema.initialize();
        var mapped = db.queryForMap("SELECT * FROM assistant_legacy_session WHERE legacy_id='old'");
        var session =
                db.queryForMap(
                        "SELECT * FROM t_agent_session WHERE id=?", mapped.get("session_id"));
        assertEquals(129, ((Number) session.get("course_id")).intValue());
        assertEquals(5, ((Number) session.get("lesson_id")).intValue());
        assertEquals("LESSON", session.get("assistant_scope"));
        assertEquals(
                1, db.queryForObject("SELECT COUNT(*) FROM t_native_rag_message", Integer.class));
        db.update(
                "INSERT INTO assistant_request(request_id,session_id,owner_id,status,input_json)"
                        + " VALUES('r1',?,7,'RUNNING','{}')",
                mapped.get("session_id"));
        schema.initialize();
        assertEquals(
                "INTERRUPTED",
                db.queryForObject(
                        "SELECT status FROM assistant_request WHERE request_id='r1'",
                        String.class));
        assertEquals(
                1,
                db.queryForObject("SELECT COUNT(*) FROM assistant_legacy_session", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM t_agent_message", Integer.class));
    }

    @Test
    void mixedCourseHistoryIsNeverGuessedIntoOneCourse() {
        var db =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                "jdbc:h2:mem:"
                                        + UUID.randomUUID()
                                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                                "sa",
                                ""));
        db.execute(
                "CREATE TABLE t_native_rag_session(id VARCHAR(36) PRIMARY KEY,owner_id INT,title"
                    + " VARCHAR(200),kb_ids TEXT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        db.execute(
                "CREATE TABLE t_native_rag_message(id VARCHAR(36) PRIMARY KEY,session_id"
                    + " VARCHAR(36),sequence_no BIGINT,role VARCHAR(16),content TEXT,evidence_json"
                    + " TEXT,created_at TIMESTAMP)");
        db.update(
                "INSERT INTO t_native_rag_session"
                    + " VALUES('mixed',7,'混合历史','[\"course:129\",\"course:130\",\"lesson:5\"]',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        new UnifiedAssistantSchema(db).initialize();
        var migrated = db.queryForMap("SELECT * FROM t_agent_session");
        assertEquals("HISTORY", migrated.get("assistant_scope"));
        assertNull(migrated.get("course_id"));
        assertNull(migrated.get("lesson_id"));
    }
}
