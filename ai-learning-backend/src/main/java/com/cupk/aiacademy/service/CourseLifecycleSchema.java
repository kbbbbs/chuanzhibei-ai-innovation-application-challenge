package com.cupk.aiacademy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Additive, restartable migration. Each legacy course is snapshotted before classification. */
@Component
public class CourseLifecycleSchema {
    private record Classification(int id,Long preparation,String stage) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    public CourseLifecycleSchema(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct public void initialize() {
        if (!tableExists("t_course")) return;
        addColumn("t_course", "setup_stage", "VARCHAR(20) DEFAULT NULL");
        addColumn("t_course", "content_mode", "VARCHAR(20)");
        addColumn("t_course", "preparation_id", "BIGINT");
        addColumn("t_course", "workflow_version", "BIGINT NOT NULL DEFAULT 0");
        addColumn("t_course", "setup_completed_at", "TIMESTAMP NULL");
        addColumn("t_teaching_class", "course_open", "BOOLEAN DEFAULT NULL");
        jdbc.execute("CREATE TABLE IF NOT EXISTS course_workflow_migration_backup(course_id INT PRIMARY KEY,snapshot_json LONGTEXT NOT NULL,classification VARCHAR(20),migrated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        var classifications = new ArrayList<Classification>();
        for (var course : jdbc.queryForList("SELECT * FROM t_course WHERE setup_stage IS NULL ORDER BY id")) {
            int id = ((Number)course.get("id")).intValue();
            Long preparation = pendingPreparation(id);
            boolean published = Integer.valueOf(1).equals(course.get("status"));
            boolean hasContent = jdbc.queryForObject("SELECT COUNT(*) FROM t_lesson l JOIN t_chapter c ON c.id=l.chapter_id WHERE c.course_id=? AND c.status=1 AND l.status=1",Integer.class,id)>0;
            String stage = published || (hasContent && preparation == null) ? "COMPLETED" : preparation != null ? "CONTENT" : "MATERIALS";
            var backup = new LinkedHashMap<String,Object>();
            backup.put("course", course);
            backup.put("classes", jdbc.queryForList("SELECT * FROM t_teaching_class WHERE course_id=?",id));
            try {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM course_workflow_migration_backup WHERE course_id=?",Integer.class,id)==0)
                    jdbc.update("INSERT INTO course_workflow_migration_backup(course_id,snapshot_json,classification) VALUES(?,?,?)",id,json.writeValueAsString(backup),stage);
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("课程迁移备份失败",failure); }
            classifications.add(new Classification(id,preparation,stage));
        }
        for (Classification item : classifications) {
            jdbc.update("UPDATE t_course SET setup_stage=?,preparation_id=?,content_mode=?,setup_completed_at=CASE WHEN ?='COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id=? AND setup_stage IS NULL",
                    item.stage(),item.preparation(),item.preparation() == null ? null : "AI",item.stage(),item.id());
        }
        jdbc.update("UPDATE t_teaching_class SET course_open=CASE WHEN status=1 AND course_id IN (SELECT id FROM t_course WHERE status=1) THEN TRUE ELSE FALSE END WHERE course_open IS NULL");
        jdbc.execute("ALTER TABLE t_course ALTER COLUMN setup_stage SET DEFAULT 'MATERIALS'");
        jdbc.execute("ALTER TABLE t_teaching_class ALTER COLUMN course_open SET DEFAULT FALSE");
    }

    private Long pendingPreparation(int courseId) {
        if (!tableExists("t_ai_preparation")) return null;
        for (var row : jdbc.queryForList("SELECT id,config_json FROM t_ai_preparation WHERE project_status='DRAFT' ORDER BY id DESC")) {
            try {
                if (json.readTree(String.valueOf(row.get("config_json"))).path("targetCourseId").asInt(-1)==courseId)
                    return ((Number)row.get("id")).longValue();
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) { /* A malformed historical draft has no confirmed course mapping. */ }
        }
        return null;
    }
    private void addColumn(String table,String column,String definition) {
        boolean exists = Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            try (var columns = connection.getMetaData().getColumns(null,null,null,null)) {
                while(columns.next()) if(table.equalsIgnoreCase(columns.getString("TABLE_NAME")) && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
            return false;
        }));
        if (!exists) jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }
    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            try(var tables=connection.getMetaData().getTables(null,null,null,new String[]{"TABLE"})) {
                while(tables.next()) if(table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
            return false;
        }));
    }
}
