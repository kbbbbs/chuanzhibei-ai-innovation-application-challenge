package com.cupk.aiacademy.installation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class InstallationConfigStoreTest {
    @TempDir Path directory;

    @Test
    void revisionsAndSecretActionsDoNotExposeCredentials() {
        var store = new InstallationConfigStore(directory, Map.of());
        var drafts = drafts();
        drafts.get("generation").put("secretAction", "REPLACE");
        drafts.get("generation").put("secretValue", "private-key-one");
        var first = store.saveModels("initial", drafts);
        String revision = (String) first.get("editableRevision");
        assertNotEquals("initial", revision);
        assertFalse(first.toString().contains("private-key-one"));
        assertFalse(store.readModels().toString().contains("private-key-one"));
        assertEquals("private-key-one", store.exportProperties(revision).get("runtime.models.generation.api-key"));
        assertEquals("REVISION_CONFLICT", assertThrows(InstallationException.class,
                () -> store.saveModels("initial", drafts)).code());

        drafts.get("generation").remove("secretValue");
        drafts.get("generation").put("secretAction", "KEEP");
        String kept = (String) store.saveModels(revision, drafts).get("editableRevision");
        assertEquals("private-key-one", store.exportProperties(kept).get("runtime.models.generation.api-key"));
        drafts.get("generation").put("secretAction", "CLEAR");
        String cleared = (String) store.saveModels(kept, drafts).get("editableRevision");
        assertEquals("", store.exportProperties(cleared).get("runtime.models.generation.api-key"));
        assertEquals("REVISION_CONFLICT", assertThrows(InstallationException.class,
                () -> store.activate(kept)).code());
        store.activate(cleared);
        assertEquals(cleared, store.readModels().get("activeRevision"));
    }

    @Test
    void environmentCredentialsAreLockedAndNeverExposed() {
        var store = new InstallationConfigStore(directory, Map.of("AI_GENERATION_API_KEY", "env-private"));
        assertFalse(store.readModels().toString().contains("env-private"));
        var drafts = drafts();
        drafts.get("generation").put("secretAction", "REPLACE");
        drafts.get("generation").put("secretValue", "replacement");
        assertEquals("FIELD_EXTERNALLY_MANAGED", assertThrows(InstallationException.class,
                () -> store.saveModels("initial", drafts)).code());
        drafts.get("generation").put("secretAction", "KEEP");
        drafts.get("generation").remove("secretValue");
        String revision = (String) store.saveModels("initial", drafts).get("editableRevision");
        assertEquals("env-private", store.exportProperties(revision).get("runtime.models.generation.api-key"));
    }

    @Test
    void draftProbeResolvesPlanningInheritanceWithoutPersisting() {
        var store = new InstallationConfigStore(directory, Map.of());
        var drafts = drafts();
        var generation = drafts.get("generation");
        generation.put("baseUrl", "https://example.com/v1");
        generation.put("model", "example-model");
        generation.put("secretAction", "REPLACE");
        generation.put("secretValue", "draft-private");
        generation.put("contextWindowTokens", 8192);
        var planning = drafts.get("planning");
        planning.put("enabled", true);
        planning.put("inheritFrom", "generation");
        var endpoint = store.resolveDraft("planning", drafts);
        assertEquals("https://example.com/v1", endpoint.getBaseUrl());
        assertEquals("example-model", endpoint.getModel());
        assertEquals("draft-private", endpoint.getApiKey());
        assertEquals(8192, endpoint.getContextWindowTokens());
        assertFalse(Files.exists(directory.resolve("models.json")));
        assertEquals("initial", store.readModels().get("editableRevision"));
        planning.put("model", "separate-model");
        assertEquals("MODEL_INHERITANCE_INVALID", assertThrows(InstallationException.class,
                () -> store.resolveDraft("planning", drafts)).code());
    }

    @Test
    void numericValuesMustBeIntegralAndInRange() {
        var store = new InstallationConfigStore(directory, Map.of());
        var drafts = drafts();
        drafts.get("generation").put("timeoutSeconds", 0);
        assertEquals("MODEL_NUMBER_INVALID", assertThrows(InstallationException.class,
                () -> store.saveModels("initial", drafts)).code());
        drafts.get("generation").put("timeoutSeconds", 1.5);
        assertEquals("MODEL_NUMBER_INVALID", assertThrows(InstallationException.class,
                () -> store.saveModels("initial", drafts)).code());
    }

    @Test
    void planningEndpointEnvironmentOverridesDoNotInheritGenerationCredentials() {
        var initial = new InstallationConfigStore(directory, Map.of());
        var drafts = configuredInheritance();
        String revision = (String) initial.saveModels("initial", drafts).get("editableRevision");

        var partial = new InstallationConfigStore(directory, Map.of("AI_PLANNING_MODEL", "planner"));
        var planning = role(partial, "planning");
        assertNull(planning.get("inheritFrom"));
        assertEquals("planner", planning.get("model"));
        assertEquals("ENVIRONMENT", ((Map<?, ?>) planning.get("source")).get("model"));
        assertEquals("CONFIGURATION_INCOMPLETE", planning.get("configuration"));
        assertFalse((Boolean) planning.get("secretConfigured"));
        assertEquals("", partial.exportProperties(revision).get("runtime.models.planning.api-key"));
        drafts.get("planning").put("inheritFrom", null);
        drafts.get("planning").put("model", "planner");
        assertEquals("MODEL_NOT_CONFIGURED", assertThrows(InstallationException.class,
                () -> partial.resolveDraft("planning", drafts)).code());
        assertEquals("MODEL_NOT_CONFIGURED", assertThrows(InstallationException.class,
                () -> partial.saveModels(revision, drafts)).code());

        var full = new InstallationConfigStore(directory, Map.of(
                "AI_PLANNING_MODEL", "planner",
                "AI_PLANNING_BASE_URL", "https://planner.example.com/v1",
                "AI_PLANNING_API_KEY", "planning-private"));
        var fullPlanning = role(full, "planning");
        assertNull(fullPlanning.get("inheritFrom"));
        assertEquals("CONFIGURED_UNVERIFIED", fullPlanning.get("configuration"));
        assertEquals("planning-private", full.exportProperties(revision).get("runtime.models.planning.api-key"));
    }

    @Test
    void planningNumericEnvironmentOverridesKeepInheritanceAndRemainLocked() {
        var initial = new InstallationConfigStore(directory, Map.of());
        String revision = (String) initial.saveModels("initial", configuredInheritance()).get("editableRevision");
        var store = new InstallationConfigStore(directory, Map.of(
                "AI_PLANNING_TIMEOUT_SECONDS", "42", "AI_PLANNING_CONTEXT_WINDOW_TOKENS", "4096"));
        var planning = role(store, "planning");
        assertEquals("generation", planning.get("inheritFrom"));
        assertEquals("INHERITED", ((Map<?, ?>) planning.get("source")).get("model"));
        assertEquals(42, planning.get("timeoutSeconds"));
        assertEquals(4096, planning.get("contextWindowTokens"));
        assertEquals("generation-private", store.exportProperties(revision).get("runtime.models.planning.api-key"));
        var drafts = configuredInheritance();
        drafts.get("planning").put("timeoutSeconds", 41);
        drafts.get("planning").put("contextWindowTokens", 4096);
        assertEquals("FIELD_EXTERNALLY_MANAGED", assertThrows(InstallationException.class,
                () -> store.saveModels(revision, drafts)).code());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> role(InstallationConfigStore store, String name) {
        return ((Map<String, Map<String, Object>>) store.readModels().get("roles")).get(name);
    }

    private static Map<String, Map<String, Object>> configuredInheritance() {
        var drafts = drafts();
        var generation = drafts.get("generation");
        generation.put("enabled", true);
        generation.put("baseUrl", "https://generation.example.com/v1");
        generation.put("model", "generation-model");
        generation.put("secretAction", "REPLACE");
        generation.put("secretValue", "generation-private");
        drafts.get("planning").put("enabled", true);
        drafts.get("planning").put("inheritFrom", "generation");
        return drafts;
    }

    private static Map<String, Map<String, Object>> drafts() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String role : new String[] {"generation", "planning", "embedding", "ocr"}) {
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("enabled", false);
            draft.put("baseUrl", "");
            draft.put("model", "");
            draft.put("secretAction", "KEEP");
            result.put(role, draft);
        }
        return result;
    }
}
