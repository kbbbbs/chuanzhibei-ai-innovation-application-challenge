package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.rag.RagAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@DependsOn("courseLifecycleSchema")
public class CourseLifecycleService {
    public record SetupRequest(String setupStage,String contentMode,Long preparationId,Long workflowVersion) {}
    public record VersionRequest(Long workflowVersion) {}
    public record PublishRequest(List<Integer> classIds,Long workflowVersion) {}
    private final JdbcTemplate jdbc;
    private final CourseMapper courses;
    private final PermissionService permissions;
    public CourseLifecycleService(JdbcTemplate jdbc,CourseMapper courses,PermissionService permissions) {
        this.jdbc=jdbc; this.courses=courses; this.permissions=permissions;
    }
    public Course setup(Integer id) { teacherCourse(id); return courses.selectById(id); }

    public int studentCount(Integer id) {
        return jdbc.queryForObject("SELECT COUNT(DISTINCT s.student_id) FROM t_class_student s JOIN t_teaching_class c ON c.id=s.class_id WHERE c.course_id=? AND c.status=1 AND c.course_open=TRUE AND s.status=1",Integer.class,id);
    }

    public Course basicForUpdate(Integer id) {
        Course course = courses.selectForUpdate(id);
        if (course == null) throw problem("404","课程不存在");
        permissions.requireCourseTeacher(course);
        return course;
    }

    public void archiveLibrary(Integer id) {
        permissions.requireAdmin();
        jdbc.update("UPDATE course_knowledge_base SET archived=TRUE WHERE course_id=?",id);
        jdbc.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP WHERE course_id=?",id);
    }

    /**
     * Deleting a course must not orphan (H2) or cascade away (MySQL) its assessments, grades and question bank.
     * Enrolment alone does not block: classes and memberships are removed with the course.
     */
    public void requireDeletable(Integer id) {
        String classes = "SELECT id FROM t_teaching_class WHERE course_id=?";
        var found = new ArrayList<String>();
        record Check(String label, String unit, String sql) {}
        for (var check : List.of(
                new Check("考试", "场", "SELECT COUNT(*) FROM t_exam_session WHERE class_id IN (" + classes + ")"),
                new Check("作业", "份", "SELECT (SELECT COUNT(*) FROM t_homework_session WHERE class_id IN (" + classes + "))+(SELECT COUNT(*) FROM t_homework WHERE class_id IN (" + classes + "))"),
                new Check("成绩记录", "条", "SELECT COUNT(*) FROM t_grade_record WHERE course_id=?"),
                new Check("试卷", "份", "SELECT (SELECT COUNT(*) FROM t_exam_paper WHERE course_id=?)+(SELECT COUNT(*) FROM t_homework_paper WHERE course_id=?)"),
                new Check("题目", "道", "SELECT COUNT(*) FROM t_question WHERE course_id=?"))) {
            int parameters = check.sql().length() - check.sql().replace("?", "").length();
            Integer count = jdbc.queryForObject(check.sql(), Integer.class, Collections.nCopies(parameters, id).toArray());
            if (count != null && count > 0) found.add(check.label() + " " + count + " " + check.unit());
        }
        if (!found.isEmpty())
            throw problem("409", "课程下还有" + String.join("、", found) + "，删除课程会一并失去这些教学记录，已阻止删除。请先在题库、试卷和考试作业中处理后再删除课程。");
    }

    /** Unpublishing by any route closes the classes too, so access always follows the published state. */
    @Transactional public void closeClasses(Integer id) {
        lock(id);
        jdbc.update("UPDATE t_teaching_class SET course_open=FALSE WHERE course_id=?", id);
        jdbc.update("UPDATE t_course SET student_count=0 WHERE id=?", id);
    }

    @Transactional public void deleteCourseClasses(Integer id) {
        permissions.requireCourseTeacher(id);
        lock(id);
        jdbc.queryForList("SELECT id FROM t_teaching_class WHERE course_id=? ORDER BY id FOR UPDATE",id);
        jdbc.update("DELETE FROM t_class_student WHERE class_id IN (SELECT id FROM t_teaching_class WHERE course_id=?)",id);
        jdbc.update("DELETE FROM t_teaching_class WHERE course_id=?",id);
    }

    @Transactional public Course saveSetup(Integer id,SetupRequest request) {
        teacherCourse(id);
        var current=lock(id);
        if ("COMPLETED".equals(current.get("setup_stage"))) throw problem("409","课程已完成创建，请从课程详情维护内容");
        version(current,request.workflowVersion());
        if (!List.of("BASIC","MATERIALS","CONTENT").contains(String.valueOf(request.setupStage()))) throw problem("400","创建步骤无效");
        if (request.contentMode()!=null && !List.of("AI","IMPORT","MANUAL").contains(request.contentMode())) throw problem("400","内容准备方式无效");
        if (request.preparationId()!=null) requirePreparation(id,request.preparationId(),false);
        jdbc.update("UPDATE t_course SET setup_stage=?,content_mode=?,preparation_id=?,workflow_version=workflow_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                request.setupStage(),request.contentMode(),request.preparationId(),id);
        return courses.selectById(id);
    }
    @Transactional public Course complete(Integer id,VersionRequest request) {
        teacherCourse(id);
        var current=lock(id);
        if ("COMPLETED".equals(current.get("setup_stage"))) return courses.selectById(id);
        version(current,request.workflowVersion());
        if (!"CONTENT".equals(current.get("setup_stage")) || current.get("content_mode")==null) throw problem("400","请先选择课程内容准备方式");
        if (current.get("title")==null || current.get("title").toString().isBlank() || current.get("category_id")==null) throw problem("400","请先完善课程基本信息");
        if ("AI".equals(current.get("content_mode"))) {
            if (!(current.get("preparation_id") instanceof Number preparation)) throw problem("400","请先应用生成草稿");
            requirePreparation(id,preparation.longValue(),true);
        }
        if ("IMPORT".equals(current.get("content_mode")) && jdbc.queryForObject("SELECT COUNT(*) FROM t_lesson l JOIN t_chapter c ON c.id=l.chapter_id WHERE c.course_id=? AND c.status=1 AND l.status=1",Integer.class,id)==0)
            throw problem("400","请先完成课件导入");
        jdbc.update("UPDATE t_course SET setup_stage='COMPLETED',setup_completed_at=CURRENT_TIMESTAMP,workflow_version=workflow_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        return courses.selectById(id);
    }
    public Map<String,Object> publication(Integer id) {
        permissions.requireCourseTeacher(id);
        var course=jdbc.queryForMap("SELECT status,workflow_version,teacher_id FROM t_course WHERE id=?",id);
        var classes=jdbc.queryForList("SELECT c.id,c.class_name,c.semester,c.status,c.course_open,COUNT(DISTINCT CASE WHEN s.status=1 THEN s.student_id ELSE NULL END) AS student_count FROM t_teaching_class c LEFT JOIN t_class_student s ON s.class_id=c.id WHERE (c.course_id=? OR (c.course_id IS NULL AND c.teacher_id=?)) AND c.status=1 GROUP BY c.id,c.class_name,c.semester,c.status,c.course_open ORDER BY c.id",id,course.get("teacher_id"));
        var rows=new ArrayList<Map<String,Object>>();
        for(var item:classes) {
            var row=new LinkedHashMap<String,Object>();
            row.put("id",item.get("id")); row.put("className",item.get("class_name")); row.put("semester",item.get("semester"));
            row.put("status",item.get("status")); row.put("courseOpen",isTrue(item.get("course_open"))); row.put("studentCount",item.get("student_count")); rows.add(row);
        }
        return Map.of("workflowVersion",course.get("workflow_version"),"status",course.get("status"),"classIds",openClasses(id),"classes",rows);
    }
    @Transactional public Map<String,Object> publish(Integer id,PublishRequest request) {
        teacherCourse(id);
        return changePublication(id,request,true);
    }
    @Transactional public Map<String,Object> updatePublication(Integer id,PublishRequest request) {
        permissions.requireAdmin();
        return changePublication(id,request,false);
    }
    private Map<String,Object> changePublication(Integer id,PublishRequest request,boolean publish) {
        var current=lock(id);
        if (request.classIds()!=null && request.classIds().stream().anyMatch(Objects::isNull)) throw problem("400","教学班编号无效");
        var ids=request.classIds()==null ? List.<Integer>of() : request.classIds().stream().distinct().sorted().toList();
        if (publish && ids.isEmpty()) throw problem("400","请选择至少一个教学班");
        if (publish && !"COMPLETED".equals(current.get("setup_stage"))) throw problem("400","请先完成课程创建");
        if (publish && !Integer.valueOf(1).equals(current.get("status")) && jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_lesson l JOIN t_chapter c ON c.id=l.chapter_id WHERE c.course_id=? AND c.status=1 AND COALESCE(l.status,1)=1",Integer.class,id)==0)
            throw problem("400","课程还没有课时，学生进入后会看到空课程。请先在课程内容中添加课时再发布");
        var parameters = new ArrayList<Object>();
        parameters.add(id);
        parameters.addAll(ids);
        String selectedClasses = ids.isEmpty() ? ""
                : " OR id IN (" + String.join(",",Collections.nCopies(ids.size(),"?")) + ")";
        // Both existing and requested waiting classes share the same lock order.
        var classes = jdbc.queryForList("SELECT id,status,teacher_id,course_id FROM t_teaching_class WHERE course_id=?"
                + selectedClasses + " ORDER BY id FOR UPDATE",parameters.toArray());
        for (Integer classId : ids) {
            boolean eligible = classes.stream().anyMatch(row -> ((Number)row.get("id")).intValue() == classId
                    && Integer.valueOf(1).equals(row.get("status"))
                    && Objects.equals(row.get("teacher_id"),current.get("teacher_id"))
                    && (row.get("course_id") == null || Objects.equals(row.get("course_id"),id)));
            if (!eligible) throw problem("400","教学班已变更或不属于本课程，请重新加载");
        }
        if (ids.equals(openClasses(id)) && (!publish || Integer.valueOf(1).equals(current.get("status")))) return publication(id);
        version(current,request.workflowVersion());
        jdbc.update("UPDATE t_teaching_class SET course_open=FALSE WHERE course_id=?",id);
        for(Integer classId:ids) jdbc.update("UPDATE t_teaching_class SET course_id=?,course_open=TRUE WHERE id=?",id,classId);
        Integer count=studentCount(id);
        jdbc.update("UPDATE t_course SET status=?,student_count=?,workflow_version=workflow_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",publish?1:current.get("status"),count,id);
        return publication(id);
    }
    private List<Integer> openClasses(Integer id) {
        return jdbc.queryForList("SELECT id FROM t_teaching_class WHERE course_id=? AND status=1 AND course_open=TRUE ORDER BY id",Integer.class,id);
    }
    private Map<String,Object> lock(Integer id) {
        var rows=jdbc.queryForList("SELECT * FROM t_course WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw problem("404","课程不存在");
        return rows.getFirst();
    }
    private void teacherCourse(Integer id) {
        var actor=UserContext.get();
        if(actor==null || !"teacher".equals(actor.getRoleCode())) throw problem("403","仅课程教师可执行此操作");
        var owners=jdbc.queryForList("SELECT teacher_id FROM t_course WHERE id=?",Integer.class,id);
        if(owners.isEmpty()) throw problem("404","课程不存在");
        if(!Objects.equals(actor.getUserId(),owners.getFirst())) throw problem("403","无权管理该课程");
    }
    private void requirePreparation(Integer id,Long preparationId,boolean applied) {
        var rows=jdbc.queryForList("SELECT teacher_id,config_json,project_status,published_course_id FROM t_ai_preparation WHERE id=?",preparationId);
        if(rows.isEmpty()) throw problem("400","备课草稿不存在");
        var row=rows.getFirst();
        try {
            int target=new ObjectMapper().readTree(String.valueOf(row.get("config_json"))).path("targetCourseId").asInt(-1);
            if(target!=id || !Objects.equals(row.get("teacher_id"),UserContext.getCurrentUserId())) throw problem("403","备课草稿不属于本课程");
        } catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw problem("400","备课关联信息无效"); }
        if(applied && !("PUBLISHED".equals(row.get("project_status")) && Objects.equals(row.get("published_course_id"),id))) throw problem("400","请先预览并应用生成草稿");
        if(applied && jdbc.queryForObject("SELECT COUNT(*) FROM course_preparation_application WHERE project_id=? AND course_id=? AND rolled_back=FALSE",Integer.class,preparationId,id)==0)
            throw problem("400","生成草稿尚未应用或已经回退，请重新检查课程内容");
    }
    private void version(Map<String,Object> course,Long version) {
        if(version==null || ((Number)course.get("workflow_version")).longValue()!=version) throw problem("409","课程已被修改，请重新加载后再操作");
    }
    private boolean isTrue(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue()==1; }
    private RagAccessException problem(String code,String message) { return new RagAccessException(code,message); }
}
