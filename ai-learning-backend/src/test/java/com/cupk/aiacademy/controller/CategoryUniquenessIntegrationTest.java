package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.CourseCategoryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class CategoryUniquenessIntegrationTest {
    @Autowired
    private CategoryController controller;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void loginAsAdmin() {
        AuthUtils.LoginUser admin = new AuthUtils.LoginUser();
        admin.setUserId(1);
        admin.setRoleCode("admin");
        UserContext.set(admin);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void duplicateCreatesAreRejectedAfterTrimming() {
        assertEquals("200", controller.add(category("Regression category")).getCode());

        Result duplicate = controller.add(category("  Regression category  "));

        assertEquals("409", duplicate.getCode());
        assertEquals("分类名称已存在", duplicate.getMsg());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_course_category WHERE name = ?", Integer.class, "Regression category"));
    }

    @Test
    void renamingToAnotherCategoryIsRejectedWithoutChangingIt() {
        controller.add(category("First category"));
        controller.add(category("Second category"));
        Integer id = categoryId("Second category");

        Result duplicate = controller.update(id, category("  First category  "));

        assertEquals("409", duplicate.getCode());
        assertEquals("Second category", jdbc.queryForObject(
                "SELECT name FROM t_course_category WHERE id = ?", String.class, id));
    }

    @Test
    void editingTheSameCategoryAndUsingADistinctNameRemainAllowed() {
        controller.add(category("Original category"));
        Integer id = categoryId("Original category");
        CourseCategoryDto update = category(" Original category ");
        update.setDescription("Updated description");

        assertEquals("200", controller.update(id, update).getCode());
        assertEquals("Updated description", jdbc.queryForObject(
                "SELECT description FROM t_course_category WHERE id = ?", String.class, id));
        assertEquals("200", controller.update(id, category("Renamed category")).getCode());
        assertEquals("200", controller.add(category("Another category")).getCode());
    }

    private Integer categoryId(String name) {
        return jdbc.queryForObject("SELECT id FROM t_course_category WHERE name = ?", Integer.class, name);
    }

    private CourseCategoryDto category(String name) {
        CourseCategoryDto request = new CourseCategoryDto();
        request.setName(name);
        return request;
    }
}
