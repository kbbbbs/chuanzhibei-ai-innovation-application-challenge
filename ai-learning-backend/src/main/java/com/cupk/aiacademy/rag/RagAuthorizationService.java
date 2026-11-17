package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.service.PermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** One identity boundary for HTTP requests and persisted background actors. Never reads legacy shared flags. */
@Service
public class RagAuthorizationService {
    private final PermissionService permissions;
    private final JdbcTemplate jdbc;

    public RagAuthorizationService(PermissionService permissions, JdbcTemplate jdbc) {
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    public RagActor currentActor() {
        AuthUtils.LoginUser context = UserContext.get();
        if (context == null || context.getUserId() == null || context.getUserId() <= 0)
            throw denied("401", "请先登录");
        return actorForUser(context.getUserId());
    }

    public RagActor actorForUser(Integer userId) {
        if (userId == null || userId <= 0) throw denied("401", "请先登录");
        AuthUtils.LoginUser context = UserContext.get();
        if (context != null && !Objects.equals(userId, context.getUserId()))
            throw denied("403", "不能使用其他用户的身份访问资料");
        // Only legacy isolated constructors omit PermissionService. Production DI requires it.
        if (permissions == null) {
            if (context == null) throw denied("401", "隔离调用缺少登录身份");
            if (!knownRole(context.getRoleCode())) throw denied("403", "用户未分配有效角色");
            return new RagActor(userId, context.getRoleCode());
        }
        AuthUtils.LoginUser active;
        try { active = permissions.resolveActiveUser(userId); }
        catch (ForbiddenException | BusinessException failure) { throw denied("403", "用户身份或角色已失效"); }
        if (active == null || !Objects.equals(userId, active.getUserId()) || !knownRole(active.getRoleCode()))
            throw denied("403", "用户身份或角色已失效");
        return new RagActor(userId, active.getRoleCode());
    }

    public void requirePersonalLibraryWrite(RagActor actor) { writer(actor); }

    public void requireCourseRead(RagActor actor, Integer courseId) {
        RagActor active = revalidate(actor);
        var user = new AuthUtils.LoginUser();
        user.setUserId(active.userId());
        user.setRoleCode(active.roleCode());
        if (permissions == null || !permissions.canReadCourseMaterials(user, courseId))
            throw denied("403", "没有该课程的有效学习或教学资格");
    }

    public void requireBaseOwner(RagActor actor, Integer ownerId) {
        RagActor active = writer(actor);
        if (!Objects.equals(active.userId(), ownerId)) throw denied("403", "只有资料所有者可以访问或修改私有资料");
    }

    /** Explicit course context, exact document version, and independent original-file permission are all required. */
    public void requireCourseDocumentRead(RagActor actor, Integer courseId, String documentId, int version, boolean download) {
        RagActor active = revalidate(actor);
        if (permissions == null || courseId == null || courseId <= 0 || documentId == null || documentId.isBlank() || version <= 0)
            throw denied("403", "课程资料授权无效");
        var user = new AuthUtils.LoginUser();user.setUserId(active.userId());user.setRoleCode(active.roleCode());
        if (!permissions.canReadCourseMaterials(user, courseId)) throw denied("403", "没有该课程的有效学习或教学资格");
        String permission = download ? "download_allowed" : "read_excerpt";
        Integer grants = jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_course_grant "
                + "WHERE course_id=? AND document_id=? AND document_version=? AND revoked_at IS NULL AND " + permission + "=TRUE",
                Integer.class, courseId, documentId, version);
        if (grants == null || grants == 0) throw denied("403", "该文档版本尚未获得所需课程授权或已撤销");
    }

    private RagActor writer(RagActor actor) {
        RagActor active = revalidate(actor);
        if (!"teacher".equals(active.roleCode()) && !"admin".equals(active.roleCode()))
            throw denied("403", "本版学生不能创建或修改个人知识库");
        return active;
    }

    private RagActor revalidate(RagActor actor) {
        if (actor == null) throw denied("401", "请先登录");
        RagActor active = actorForUser(actor.userId());
        if (!Objects.equals(active.roleCode(), actor.roleCode())) throw denied("403", "资料访问身份已发生变化");
        return active;
    }

    private static boolean knownRole(String role) { return role != null && List.of("teacher", "student", "admin").contains(role); }
    private static RagAccessException denied(String code, String message) { return new RagAccessException(code, message); }
}
