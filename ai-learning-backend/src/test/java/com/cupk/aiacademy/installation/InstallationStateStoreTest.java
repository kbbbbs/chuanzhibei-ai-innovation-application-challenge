package com.cupk.aiacademy.installation;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstallationStateStoreTest {
    @TempDir Path directory;

    @Test
    void databaseRevisionsPreserveActiveCredentialsAndSupportRollback() {
        var store = new InstallationStateStore(directory, Map.of());
        var active = new DatabaseInstaller.DatabaseSettings("jdbc:mysql://a:3306/db", "userA", "passwordA");
        var pending = new DatabaseInstaller.DatabaseSettings("jdbc:mysql://b:3306/db", "userB", "passwordB");
        store.markInstalled(active, store.installationId());
        store.stageDatabase("A", active);
        store.confirmRevision("A");
        store.stageDatabase("B", pending);

        assertEquals(active, store.savedDatabase("A"));
        assertEquals(pending, store.savedDatabase("B"));
        assertEquals("passwordA", store.exportWorkerProperties("").get("spring.datasource.password"));

        var resolver = new DatabaseDraftResolver(Map.of(), store);
        var draft = Map.<String, Object>of("mode", "EXTERNAL", "host", "b", "port", 3306,
                "database", "db", "username", "userB", "passwordAction", "KEEP");
        assertEquals("passwordB", resolver.resolve(draft, "B").password());

        store.confirmRevision("B");
        assertEquals("passwordB", store.exportWorkerProperties("").get("spring.datasource.password"));
        store.copyDatabase("A", "C");
        assertEquals(active, store.savedDatabase("C"));
    }

    @Test
    void externallyManagedDatabaseFieldsCannotBeChanged() {
        var environment = Map.of("DB_URL", "jdbc:mysql://db:3306/app?sslMode=DISABLED",
                "DB_USERNAME", "external-user", "DB_PASSWORD", "external-secret");
        var store = new InstallationStateStore(directory, environment);
        var resolver = new DatabaseDraftResolver(environment, store);
        var draft = new java.util.HashMap<String, Object>(Map.of("mode", "EXTERNAL", "host", "db",
                "port", 3306, "database", "app", "username", "external-user",
                "passwordAction", "KEEP", "tls", "DISABLED"));
        assertEquals("external-secret", resolver.resolve(draft).password());
        draft.put("host", "other");
        assertEquals("FIELD_EXTERNALLY_MANAGED",
                assertThrows(InstallationException.class, () -> resolver.resolve(draft)).code());
    }
}
