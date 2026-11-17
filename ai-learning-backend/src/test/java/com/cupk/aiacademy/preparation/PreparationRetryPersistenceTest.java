package com.cupk.aiacademy.preparation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PreparationRetryPersistenceTest {
    @Test
    void retryClearsThePersistedFailureAndKeepsDraftContentAndVersionProtection() throws Exception {
        var database = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:preparation-retry-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("preparation-retry", new JdbcTransactionFactory(), database));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        configuration.addInterceptor(interceptor);
        configuration.addMapper(PreparationMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        try (var session = factory.openSession(true)) {
            try (var sql = session.getConnection().createStatement()) {
                sql.execute("""
                        CREATE TABLE t_ai_preparation (
                          id BIGINT PRIMARY KEY, teacher_id INT, title VARCHAR(200), config_json CLOB,
                          knowledge_base_ids_json CLOB, owned_knowledge_base_id VARCHAR(100), source_files_json CLOB,
                          outline_json CLOB, lessons_json CLOB, questions_json CLOB, project_status VARCHAR(30),
                          current_stage VARCHAR(50), generation_status VARCHAR(30), progress INT, error_message VARCHAR(2000),
                          published_course_id INT, version INT, created_at TIMESTAMP, updated_at TIMESTAMP)
                        """);
            }
            var mapper = session.getMapper(PreparationMapper.class);
            var project = new PreparationProject();
            project.setId(1L);
            project.setTeacherId(7);
            project.setProjectStatus("DRAFT");
            project.setGenerationStatus("FAILED");
            project.setErrorMessage("上次调用超时");
            project.setOutlineJson("{\"chapters\":[]}");
            project.setLessonsJson("[]");
            project.setVersion(3);
            mapper.insert(project);
            PreparationProject retry = mapper.selectById(1L);
            session.clearCache();
            PreparationProject stale = mapper.selectById(1L);

            retry.setGenerationStatus("RUNNING");
            retry.setErrorMessage(null);
            assertEquals(1, mapper.updateById(retry));

            var stored = mapper.selectById(1L);
            assertEquals("RUNNING", stored.getGenerationStatus());
            assertNull(stored.getErrorMessage());
            assertEquals(project.getOutlineJson(), stored.getOutlineJson());
            assertEquals("[]", stored.getLessonsJson());
            assertEquals(4, stored.getVersion());
            stale.setGenerationStatus("SUCCEEDED");
            assertEquals(0, mapper.updateById(stale));
            assertEquals("RUNNING", mapper.selectById(1L).getGenerationStatus());
        }
    }
}
