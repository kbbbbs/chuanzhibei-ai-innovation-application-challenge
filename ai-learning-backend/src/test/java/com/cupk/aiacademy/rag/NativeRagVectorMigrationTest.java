package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Mock JDBC metadata only. No existing MySQL or application database is opened by these tests. */
class NativeRagVectorMigrationTest {
    private static final String WIDEN_VECTOR =
            "ALTER TABLE native_rag_chunk MODIFY COLUMN vector_json LONGTEXT NULL";
    @TempDir Path temp;

    @Test
    void existingMySqlTextColumnIsWidenedOnceAcrossRepeatedInitialization() throws Exception {
        var columnType = new AtomicReference<>("TEXT");
        var fixture = new MetadataFixture("MySQL", columnType);
        doAnswer(invocation -> {
            if (WIDEN_VECTOR.equals(invocation.getArgument(0))) columnType.set("LONGTEXT");
            return null;
        }).when(fixture.jdbc).execute(anyString());

        fixture.service.initializeBaseTables();
        fixture.service.initializeBaseTables();

        verify(fixture.jdbc, times(1)).execute(WIDEN_VECTOR);
        verify(fixture.metadata, times(2)).getColumns("isolated-catalog", null,
                "native_rag_chunk", "vector_json");
        verify(fixture.connection, times(2)).close();
        verify(fixture.columns, times(2)).close();
        var statements = ArgumentCaptor.forClass(String.class);
        verify(fixture.jdbc, times(7)).execute(statements.capture());
        assertTrue(statements.getAllValues().stream().allMatch(sql ->
                        sql.startsWith("CREATE TABLE IF NOT EXISTS ") || sql.equals(WIDEN_VECTOR)),
                "Startup must preserve existing documents, chunks and source metadata");
    }

    @ParameterizedTest
    @CsvSource({"H2,TEXT", "MySQL,LONGTEXT", "MySQL,MEDIUMTEXT", "PostgreSQL,TEXT"})
    void otherDatabaseProductsOrAlreadyWideColumnsAreNeverAltered(String product, String type) throws Exception {
        var fixture = new MetadataFixture(product, new AtomicReference<>(type));
        fixture.service.initializeBaseTables();

        var statements = ArgumentCaptor.forClass(String.class);
        verify(fixture.jdbc, times(3)).execute(statements.capture());
        assertTrue(statements.getAllValues().stream().allMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS ")));
        if (!product.equals("MySQL")) {
            verify(fixture.metadata, never()).getColumns(any(), any(), any(), any());
        }
    }

    @Test
    void similarMetadataNamesAreSkippedUntilTheExactTableAndColumnAreFound() throws Exception {
        var fixture = new MetadataFixture("MySQL", List.of(
                new ColumnMetadata("nativeXragXchunk", "vector_json", "LONGTEXT"),
                new ColumnMetadata("native_rag_chunk", "vectorXjson", "LONGTEXT"),
                new ColumnMetadata("native_rag_chunk", "vector_json", "TEXT")));

        fixture.service.initializeBaseTables();

        verify(fixture.jdbc).execute(WIDEN_VECTOR);
    }

    @ParameterizedTest
    @CsvSource({"nativeXragXchunk,vector_json", "native_rag_chunk,vectorXjson", "nativeXragXchunk,vectorXjson"})
    void similarMetadataNamesWithoutTheExactColumnNeverTriggerAlter(String tableName, String columnName) throws Exception {
        var fixture = new MetadataFixture("MySQL", List.of(new ColumnMetadata(tableName, columnName, "TEXT")));

        fixture.service.initializeBaseTables();

        verify(fixture.jdbc, never()).execute(WIDEN_VECTOR);
        verify(fixture.jdbc, times(3)).execute(anyString());
    }

    private record ColumnMetadata(String table, String column, AtomicReference<String> type) {
        private ColumnMetadata(String table, String column, String type) {
            this(table, column, new AtomicReference<>(type));
        }
    }

    private class MetadataFixture {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final Connection connection = mock(Connection.class);
        private final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        private final ResultSet columns = mock(ResultSet.class);
        private final NativeRagService service;

        private MetadataFixture(String product, AtomicReference<String> columnType) throws Exception {
            this(product, List.of(new ColumnMetadata("native_rag_chunk", "vector_json", columnType)));
        }

        private MetadataFixture(String product, List<ColumnMetadata> rows) throws Exception {
            DataSource dataSource = mock(DataSource.class);
            var rowIndex = new AtomicInteger(-1);
            when(jdbc.getDataSource()).thenReturn(dataSource);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(connection.getCatalog()).thenReturn("isolated-catalog");
            when(metadata.getDatabaseProductName()).thenReturn(product);
            when(metadata.getColumns("isolated-catalog", null, "native_rag_chunk", "vector_json"))
                    .thenAnswer(ignored -> {
                        rowIndex.set(-1);
                        return columns;
                    });
            when(columns.next()).thenAnswer(ignored -> rowIndex.incrementAndGet() < rows.size());
            when(columns.getString(anyString())).thenAnswer(invocation -> {
                var row = rows.get(rowIndex.get());
                return switch ((String) invocation.getArgument(0)) {
                    case "TABLE_NAME" -> row.table();
                    case "COLUMN_NAME" -> row.column();
                    case "TYPE_NAME" -> row.type().get();
                    default -> null;
                };
            });
            var mapper = new ObjectMapper();
            service = new NativeRagService(jdbc, new RagDocumentParser(),
                    new RagEmbeddingClient(false, "", "", "", mapper), mapper, temp.toString());
        }
    }
}
