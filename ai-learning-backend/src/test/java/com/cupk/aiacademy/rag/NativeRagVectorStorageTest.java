package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** H2 does not enforce MySQL TEXT's 65535-byte limit, so check both MySQL DDL contracts explicitly. */
class NativeRagVectorStorageTest {
    @TempDir Path temp;

    @Test
    void applicationInitializerDeclaresVectorStorageBeyondMySqlTextCapacity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.getDataSource()).thenReturn(new DriverManagerDataSource("jdbc:h2:mem:unused", "sa", ""));
        var mapper = new ObjectMapper();
        var embedding = new RagEmbeddingClient(false, "", "", "", mapper);
        var service = new NativeRagService(jdbc, new RagDocumentParser(), embedding, mapper, temp.toString());
        service.initializeBaseTables();
        var ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).execute(ddl.capture());
        String chunkDdl = ddl.getAllValues().stream()
                .filter(sql -> sql.contains("CREATE TABLE IF NOT EXISTS native_rag_chunk"))
                .findFirst().orElseThrow();
        assertTrue(chunkDdl.matches("(?is).*\\bvector_json\\s+LONGTEXT\\b.*"),
                "A normalized 4096-double JSON vector can exceed MySQL TEXT's 65535-byte limit");
    }

    @Test
    void checkedInSchemaDeclaresTheSameLargeVectorStorage() throws Exception {
        Path schema = Path.of("database", "native_rag.sql");
        if (!Files.isRegularFile(schema)) schema = Path.of("..", "database", "native_rag.sql");
        String ddl = Files.readString(schema);
        assertTrue(ddl.matches("(?is).*\\bvector_json\\s+LONGTEXT\\b.*"),
                "Fresh MySQL installations must support full 4096-double JSON vectors");
    }
}
