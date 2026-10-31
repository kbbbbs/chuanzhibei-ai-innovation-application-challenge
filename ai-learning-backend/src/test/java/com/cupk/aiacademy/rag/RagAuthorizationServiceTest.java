package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RagAuthorizationServiceTest {
    private PermissionService permissions;
    private JdbcTemplate jdbc;
    private RagAuthorizationService service;

    @BeforeEach void setup() {
        permissions=mock(PermissionService.class);
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:rag-auth;MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE IF NOT EXISTS native_rag_course_grant(id VARCHAR(36) PRIMARY KEY,owner_id INTEGER,course_id INTEGER,document_id VARCHAR(36),document_version INTEGER,read_excerpt BOOLEAN,download_allowed BOOLEAN,revoked_at TIMESTAMP)");
        jdbc.update("DELETE FROM native_rag_course_grant");
        service=new RagAuthorizationService(permissions,jdbc);
        when(permissions.resolveActiveUser(1)).thenReturn(login(1,"teacher"));
        when(permissions.resolveActiveUser(2)).thenReturn(login(2,"student"));
        when(permissions.resolveActiveUser(3)).thenReturn(login(3,"admin"));
    }
    @AfterEach void cleanup(){UserContext.clear();}

    @Test void currentIdentityIsRevalidatedAndWorkerIdentityNeedsAnActiveDatabaseAccount() {
        denied("401",service::currentActor);
        UserContext.set(login(1,"teacher"));
        assertEquals(new RagActor(1,"teacher"),service.currentActor());
        verify(permissions).resolveActiveUser(1);
        denied("403",()->service.actorForUser(2));
        UserContext.clear();assertEquals(new RagActor(2,"student"),service.actorForUser(2));
        when(permissions.resolveActiveUser(2)).thenThrow(new ForbiddenException("inactive"));
        denied("403",()->service.actorForUser(2));
    }

    @Test void fallbackForIsolatedServiceTestsRequiresARealContextAndKnownRole() {
        var isolated=new RagAuthorizationService(null,jdbc);
        denied("401",()->isolated.actorForUser(1));
        UserContext.set(login(1,"teacher"));assertEquals(new RagActor(1,"teacher"),isolated.currentActor());
        denied("403",()->isolated.actorForUser(2));
        UserContext.set(login(1,"guest"));denied("403",isolated::currentActor);
    }

    @Test void studentsCannotWritePersonalLibrariesAndAdminCannotOverrideTheOwner() {
        denied("403",()->service.requirePersonalLibraryWrite(new RagActor(2,"student")));
        denied("403",()->service.requirePersonalLibraryWrite(new RagActor(2,"teacher")));
        assertDoesNotThrow(()->service.requireBaseOwner(new RagActor(1,"teacher"),1));
        denied("403",()->service.requireBaseOwner(new RagActor(3,"admin"),1));
        denied("403",()->service.requireBaseOwner(new RagActor(2,"student"),2));
        assertDoesNotThrow(()->service.requireBaseOwner(new RagActor(3,"admin"),3));
    }

    @Test void aRoleChangeOrDisabledAccountInvalidatesAnEarlierActorSnapshot() {
        var actor=service.actorForUser(1);
        when(permissions.resolveActiveUser(1)).thenReturn(login(1,"student"));
        denied("403",()->service.requireBaseOwner(actor,1));
        when(permissions.resolveActiveUser(1)).thenThrow(new ForbiddenException("inactive"));
        denied("403",()->service.requireCourseDocumentRead(actor,10,"doc",1,false));
    }

    @Test void aGrantDoesNotReplaceTheCourseMembershipCheckAndMustMatchTheExactVersion() {
        var student=new RagActor(2,"student");grant(true,false);
        denied("403",()->service.requireCourseDocumentRead(student,10,"doc",1,false));
        when(permissions.canReadCourseMaterials(any(),eq(10))).thenReturn(true);
        assertDoesNotThrow(()->service.requireCourseDocumentRead(student,10,"doc",1,false));
        denied("403",()->service.requireCourseDocumentRead(student,10,"doc",2,false));
        denied("403",()->service.requireCourseDocumentRead(student,11,"doc",1,false));
        denied("403",()->service.requireCourseDocumentRead(student,10,"other",1,false));
        jdbc.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP");
        denied("403",()->service.requireCourseDocumentRead(student,10,"doc",1,false));
    }

    @Test void excerptAndOriginalFilePermissionsAreIndependent() {
        var student=new RagActor(2,"student");when(permissions.canReadCourseMaterials(any(),eq(10))).thenReturn(true);
        grant(true,false);
        assertDoesNotThrow(()->service.requireCourseDocumentRead(student,10,"doc",1,false));
        denied("403",()->service.requireCourseDocumentRead(student,10,"doc",1,true));
        jdbc.update("UPDATE native_rag_course_grant SET read_excerpt=FALSE,download_allowed=TRUE");
        denied("403",()->service.requireCourseDocumentRead(student,10,"doc",1,false));
        assertDoesNotThrow(()->service.requireCourseDocumentRead(student,10,"doc",1,true));
    }

    @Test void courseDirectoryAccessAndAnAdminRoleAloneDoNotRevealUngrantedMaterial() {
        var admin=new RagActor(3,"admin");when(permissions.canReadCourseMaterials(any(),eq(10))).thenReturn(true);
        denied("403",()->service.requireCourseDocumentRead(admin,10,"private",1,false));
        var student=new RagActor(2,"student");
        denied("403",()->service.requireCourseDocumentRead(student,10,"private",1,true));
    }

    private void grant(boolean read,boolean download) {
        jdbc.update("INSERT INTO native_rag_course_grant VALUES('grant',1,10,'doc',1,?,?,NULL)",read,download);
    }
    private static AuthUtils.LoginUser login(int id,String role) {
        var user=new AuthUtils.LoginUser();user.setUserId(id);user.setRoleCode(role);return user;
    }
    private static void denied(String code,org.junit.jupiter.api.function.Executable call) {
        assertEquals(code,assertThrows(RagAccessException.class,call).code());
    }
}
