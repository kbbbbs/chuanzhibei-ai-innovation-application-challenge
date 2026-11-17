package com.cupk.aiacademy.installation;

import com.cupk.aiacademy.model.RuntimeModelProperties;
import com.cupk.aiacademy.model.RuntimeModelPolicy;
import com.cupk.aiacademy.model.ModelConfigurationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.Function;

/** Private revisions are atomically replaced; all public configuration views omit credentials. */
public final class InstallationConfigStore {
    private static final List<String> ROLES = List.of("generation", "planning", "embedding", "ocr");
    private static final Set<String> FIELDS = Set.of("enabled", "baseUrl", "model", "timeoutSeconds",
            "contextWindowTokens", "inheritFrom", "secretAction", "secretValue");
    private final Path directory;
    private final Path file;
    private final Map<String, String> environment;
    private final ObjectMapper json = new ObjectMapper();

    public InstallationConfigStore(Path directory, Map<String, String> environment) {
        this.directory = directory.toAbsolutePath().normalize();
        this.file = this.directory.resolve("models.json");
        this.environment = Map.copyOf(environment);
    }

    public synchronized Map<String, Object> readModels() {
        return view(load());
    }

    public synchronized Map<String, Object> saveModels(String expectedRevision,
                                                       Map<String, Map<String, Object>> drafts) {
        return mutate(state -> {
            if (!Objects.equals(expectedRevision, editable(state)))
                throw fail(409, "REVISION_CONFLICT");
            Map<String, Map<String, Object>> roles = validateDrafts(drafts, roles(state, editable(state)));
            String revision = UUID.randomUUID().toString();
            revisions(state).put(revision, roles);
            state.put("pendingRevision", revision);
            return view(state);
        });
    }

    /** Internal worker hand-off. Never return this map through an HTTP response or logger. */
    public synchronized Map<String, String> exportProperties(String revision) {
        var state = load();
        var effective = effectiveRoles(roles(state, revision));
        Map<String, String> result = new LinkedHashMap<>();
        for (String role : ROLES) {
            var values = effective.get(role);
            String prefix = "runtime.models." + role + ".";
            for (String field : List.of("enabled", "baseUrl", "model", "secret", "timeoutSeconds", "contextWindowTokens")) {
                String property = switch (field) {
                    case "baseUrl" -> "base-url";
                    case "secret" -> "api-key";
                    case "timeoutSeconds" -> "timeout-seconds";
                    case "contextWindowTokens" -> "context-window-tokens";
                    default -> field;
                };
                result.put(prefix + property, String.valueOf(values.get(field)));
                if (role.equals("embedding")) result.put("rag.embedding." + property, String.valueOf(values.get(field)));
                if (role.equals("ocr")) result.put("ocr." + property, String.valueOf(values.get(field)));
            }
        }
        return result;
    }

    public synchronized void activate(String revision) {
        mutate(state -> {
            if (!Objects.equals(revision, state.get("pendingRevision")))
                throw fail(409, "REVISION_CONFLICT");
            roles(state, revision);
            state.put("previousRevision", state.get("activeRevision"));
            state.put("activeRevision", revision);
            state.put("pendingRevision", null);
            return null;
        });
    }

    public synchronized Map<String, Object> rollbackTo(String revision) {
        return mutate(state -> {
            var target = roles(state, revision);
            String next = UUID.randomUUID().toString();
            revisions(state).put(next, deepCopy(target));
            state.put("pendingRevision", next);
            return view(state);
        });
    }

    /** A temporary endpoint for an explicit draft probe; no revision is written. */
    public synchronized RuntimeModelProperties.Endpoint resolveDraft(String role,
                                                       Map<String, Map<String, Object>> drafts) {
        if (!ROLES.contains(role)) throw fail(422, "MODEL_ROLE_INVALID");
        var state = load();
        var resolved = effectiveRoles(validateDrafts(drafts, roles(state, editable(state)), role)).get(role);
        var endpoint = new RuntimeModelProperties.Endpoint();
        endpoint.setEnabled(Boolean.TRUE.equals(resolved.get("enabled")));
        endpoint.setBaseUrl(String.valueOf(resolved.get("baseUrl")));
        endpoint.setModel(String.valueOf(resolved.get("model")));
        endpoint.setApiKey(String.valueOf(resolved.get("secret")));
        endpoint.setTimeoutSeconds(((Number) resolved.get("timeoutSeconds")).intValue());
        endpoint.setContextWindowTokens(((Number) resolved.get("contextWindowTokens")).intValue());
        return endpoint;
    }

    public void requireAllowedEndpoint(String value) {
        if (value.isEmpty()) return;
        try {
            RuntimeModelPolicy.requireAllowedEndpoint(value,
                    environment.getOrDefault("INSTALLATION_MODEL_HTTP_ALLOWLIST", ""));
        } catch (ModelConfigurationException ignored) { throw fail(422, "MODEL_ENDPOINT_INVALID"); }
    }

    private Map<String, Map<String, Object>> validateDrafts(Map<String, Map<String, Object>> drafts,
                                                          Map<String, Map<String, Object>> saved) {
        return validateDrafts(drafts, saved, null);
    }

    private Map<String, Map<String, Object>> validateDrafts(Map<String, Map<String, Object>> drafts,
                                                          Map<String, Map<String, Object>> saved, String probeRole) {
        if (drafts == null || !drafts.keySet().equals(new HashSet<>(ROLES)))
            throw fail(422, "MODEL_ROLES_INVALID");
        var before = effectiveRoles(saved);
        Map<String, Map<String, Object>> next = new LinkedHashMap<>();
        for (String role : ROLES) {
            Object rawDraft = ((Map<?, ?>) drafts).get(role);
            if (!(rawDraft instanceof Map<?, ?> raw) || !FIELDS.containsAll(raw.keySet()))
                throw fail(422, "MODEL_FIELDS_INVALID");
            @SuppressWarnings("unchecked") Map<String, Object> draft = (Map<String, Object>) rawDraft;
            Map<String, Object> value = new LinkedHashMap<>();
            if (!(draft.get("enabled") instanceof Boolean)) throw fail(422, "MODEL_ENABLED_INVALID");
            value.put("enabled", draft.get("enabled"));
            value.put("baseUrl", string(draft, "baseUrl"));
            value.put("model", string(draft, "model"));
            value.put("timeoutSeconds", integer(draft.getOrDefault("timeoutSeconds", 30), 1, 300));
            value.put("contextWindowTokens", integer(draft.getOrDefault("contextWindowTokens", 0), 0, Integer.MAX_VALUE));
            Object inherit = draft.get("inheritFrom");
            if (inherit != null && !(role.equals("planning") && inherit.equals("generation")))
                throw fail(422, "MODEL_INHERITANCE_INVALID");
            value.put("inheritFrom", inherit);
            String action = string(draft, "secretAction");
            if (!Set.of("KEEP", "REPLACE", "CLEAR").contains(action)) throw fail(422, "MODEL_SECRET_ACTION_INVALID");
            boolean supplied = draft.containsKey("secretValue");
            if ((action.equals("KEEP") || action.equals("CLEAR")) && supplied)
                throw fail(422, "MODEL_SECRET_ACTION_INVALID");
            if (inherit != null && (!string(draft, "baseUrl").isEmpty() || !string(draft, "model").isEmpty()
                    || supplied || !action.equals("KEEP")))
                throw fail(422, "MODEL_INHERITANCE_INVALID");
            String secret = switch (action) {
                case "REPLACE" -> {
                    String replacement = string(draft, "secretValue");
                    if (replacement.isBlank() || replacement.matches("[*\\u2022]+"))
                        throw fail(422, "MODEL_SECRET_REQUIRED");
                    yield replacement;
                }
                case "CLEAR" -> "";
                default -> String.valueOf(saved.getOrDefault(role, Map.of()).getOrDefault("secret", ""));
            };
            if (!action.equals("KEEP") || saved.getOrDefault(role, Map.of()).containsKey("secret"))
                value.put("secret", secret);
            var current = before.get(role);
            @SuppressWarnings("unchecked") Set<String> locked = new HashSet<>((List<String>) current.get("lockedFields"));
            for (String field : locked) {
                if (field.equals("secret")) {
                    if (!action.equals("KEEP")) throw fail(409, "FIELD_EXTERNALLY_MANAGED");
                } else if (!Objects.equals(value.get(field), current.get(field)))
                    throw fail(409, "FIELD_EXTERNALLY_MANAGED");
                if (saved.getOrDefault(role, Map.of()).containsKey(field))
                    value.put(field, saved.get(role).get(field));
                else value.remove(field);
            }
            requireAllowedEndpoint(String.valueOf(value.getOrDefault("baseUrl", "")));
            next.put(role, value);
        }
        var effective = effectiveRoles(next);
        for (String role : ROLES) {
            var value = effective.get(role);
            requireAllowedEndpoint(String.valueOf(value.get("baseUrl")));
            if ((probeRole == null || probeRole.equals(role)) && Boolean.TRUE.equals(value.get("enabled"))
                    && (String.valueOf(value.get("baseUrl")).isBlank()
                    || String.valueOf(value.get("model")).isBlank() || !configuredSecret(value.get("secret"))))
                throw fail(422, "MODEL_NOT_CONFIGURED");
        }
        return next;
    }

    private Map<String, Map<String, Object>> effectiveRoles(Map<String, Map<String, Object>> saved) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String role : ROLES) {
            var persisted = saved.getOrDefault(role, Map.of());
            Map<String, Object> values = new LinkedHashMap<>();
            Map<String, String> sources = new LinkedHashMap<>();
            List<String> locked = new ArrayList<>();
            for (String field : List.of("enabled", "baseUrl", "model", "secret", "timeoutSeconds", "contextWindowTokens")) {
                String direct = environment.get(envName(role, field));
                Object value;
                String source;
                if (direct != null) {
                    value = parseEnvironment(field, direct); source = "ENVIRONMENT";
                } else if (persisted.containsKey(field)) {
                    value = persisted.get(field); source = "PRIVATE_FILE";
                } else {
                    String alias = alias(role, field);
                    value = alias == null ? defaultValue(role, field) : parseEnvironment(field, alias);
                    source = alias == null ? "DEFAULT" : "ENVIRONMENT";
                }
                values.put(field, value);
                sources.put(field, source);
                if (source.equals("ENVIRONMENT")) locked.add(field);
            }
            Object inheritance = persisted.get("inheritFrom");
            if (role.equals("planning")) {
                if (planningEndpointExternallyManaged()) inheritance = null;
                else if (!persisted.containsKey("inheritFrom")) inheritance = "generation";
            }
            values.put("inheritFrom", inheritance);
            if ("generation".equals(inheritance) && role.equals("planning")) {
                var generation = result.get("generation");
                for (String field : List.of("baseUrl", "model", "secret", "contextWindowTokens")) {
                    if (locked.contains(field)) continue;
                    values.put(field, generation.get(field)); sources.put(field, "INHERITED");
                }
            }
            values.put("source", sources);
            values.put("lockedFields", locked);
            result.put(role, values);
        }
        return result;
    }

    private boolean planningEndpointExternallyManaged() {
        return List.of("baseUrl", "model", "secret").stream()
                .anyMatch(field -> environment.containsKey(envName("planning", field))
                        || alias("planning", field) != null);
    }

    private String envName(String role, String field) {
        String prefix = switch (role) {
            case "generation" -> "AI_GENERATION_";
            case "planning" -> "AI_PLANNING_";
            case "embedding" -> "RAG_EMBEDDING_";
            default -> "OCR_";
        };
        return prefix + switch (field) {
            case "baseUrl" -> "BASE_URL";
            case "secret" -> "API_KEY";
            case "timeoutSeconds" -> "TIMEOUT_SECONDS";
            case "contextWindowTokens" -> "CONTEXT_WINDOW_TOKENS";
            default -> field.toUpperCase(Locale.ROOT);
        };
    }

    private String alias(String role, String field) {
        List<String> names = switch (role + "." + field) {
            case "generation.baseUrl" -> List.of("AI_OPENAI_BASE_URL", "BASE_URL");
            case "generation.model" -> List.of("AI_OPENAI_MODEL");
            case "generation.secret" -> List.of("AI_OPENAI_API_KEY", "API_KEY");
            case "generation.timeoutSeconds" -> List.of("AI_AGENT_MODEL_TIMEOUT_SECONDS");
            case "planning.model" -> List.of("AI_AGENT_PLANNER_MODEL");
            case "embedding.secret" -> List.of("RAG_EMBEDDING_APIKEY", "SILICONFLOW_API_KEY");
            case "ocr.secret" -> List.of("OCR_APIKEY", "SILICONFLOW_API_KEY");
            default -> List.of();
        };
        for (String name : names) if (environment.containsKey(name)) return environment.get(name);
        return null;
    }

    private Object defaultValue(String role, String field) {
        return switch (field) {
            case "enabled" -> !role.equals("embedding");
            case "timeoutSeconds" -> role.equals("ocr") ? 60 : role.equals("planning") ? 10 : 30;
            case "contextWindowTokens" -> 0;
            default -> "";
        };
    }

    private Object parseEnvironment(String field, String value) {
        if (field.equals("enabled")) {
            if (!Set.of("true", "false").contains(value.toLowerCase(Locale.ROOT)))
                throw fail(422, "MODEL_ENVIRONMENT_INVALID");
            return Boolean.parseBoolean(value);
        }
        if (field.equals("timeoutSeconds")) return parseInteger(value, 1, 300);
        if (field.equals("contextWindowTokens")) return parseInteger(value, 0, Integer.MAX_VALUE);
        return value.strip();
    }

    private int parseInteger(String value, int min, int max) {
        try { return integer(Integer.valueOf(value), min, max); }
        catch (NumberFormatException ignored) { throw fail(422, "MODEL_ENVIRONMENT_INVALID"); }
    }

    private int integer(Object value, int min, int max) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                || !(value instanceof Number number)
                || number.longValue() < min || number.longValue() > max)
            throw fail(422, "MODEL_NUMBER_INVALID");
        return number.intValue();
    }

    private String string(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof String text)) throw fail(422, "MODEL_FIELDS_INVALID");
        return text.strip();
    }

    private boolean configuredSecret(Object secret) {
        return secret instanceof String value && !value.isBlank() && !value.equals("NOT_CONFIGURED");
    }

    private Map<String, Object> view(Map<String, Object> state) {
        var effective = effectiveRoles(roles(state, editable(state)));
        Map<String, Object> roleViews = new LinkedHashMap<>();
        for (String role : ROLES) {
            Map<String, Object> value = new LinkedHashMap<>(effective.get(role));
            Object secret = value.remove("secret");
            boolean configured = configuredSecret(secret);
            value.put("secretConfigured", configured);
            String status = !Boolean.TRUE.equals(value.get("enabled")) ? "MODEL_DISABLED"
                    : !configured || String.valueOf(value.get("baseUrl")).isBlank()
                    || String.valueOf(value.get("model")).isBlank() ? "MODEL_NOT_CONFIGURED" : "CONFIGURED_UNVERIFIED";
            if (role.equals("planning") && planningEndpointExternallyManaged() && status.equals("MODEL_NOT_CONFIGURED"))
                status = "CONFIGURATION_INCOMPLETE";
            try { requireAllowedEndpoint(String.valueOf(value.get("baseUrl"))); }
            catch (InstallationException invalid) { status = invalid.code(); }
            value.put("configuration", status);
            roleViews.put(role, value);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeRevision", state.get("activeRevision"));
        result.put("pendingRevision", state.get("pendingRevision"));
        result.put("editableRevision", editable(state));
        result.put("roles", roleViews);
        result.put("restartRequired", state.get("pendingRevision") != null);
        result.put("lifecycleSupported", true);
        return result;
    }

    private String editable(Map<String, Object> state) {
        return String.valueOf(state.get("pendingRevision") == null ? state.get("activeRevision") : state.get("pendingRevision"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> revisions(Map<String, Object> state) {
        return (Map<String, Object>) state.get("revisions");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> roles(Map<String, Object> state, String revision) {
        Object value = revisions(state).get(revision);
        if (!(value instanceof Map<?, ?>)) throw fail(409, "REVISION_CONFLICT");
        return (Map<String, Map<String, Object>>) value;
    }

    private Map<String, Map<String, Object>> deepCopy(Map<String, Map<String, Object>> value) {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        value.forEach((key, fields) -> copy.put(key, new LinkedHashMap<>(fields)));
        return copy;
    }

    private Map<String, Object> load() {
        try {
            if (Files.isSymbolicLink(file)) throw fail(503, "MODEL_CONFIG_UNAVAILABLE");
            if (Files.exists(file)) return json.readValue(Files.readAllBytes(file), new TypeReference<>() {});
            Map<String, Object> initial = new LinkedHashMap<>();
            initial.put("activeRevision", "initial");
            initial.put("pendingRevision", null);
            initial.put("previousRevision", null);
            initial.put("revisions", new LinkedHashMap<>(Map.of("initial", new LinkedHashMap<>())));
            return initial;
        } catch (InstallationException error) { throw error; }
        catch (Exception ignored) { throw fail(503, "MODEL_CONFIG_UNAVAILABLE"); }
    }

    private <T> T mutate(Function<Map<String, Object>, T> action) {
        // JVM file locks throw for overlapping channels; serialize them before the OS lock.
        synchronized (InstallationConfigStore.class) { return mutateLocked(action); }
    }

    private <T> T mutateLocked(Function<Map<String, Object>, T> action) {
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) throw fail(503, "MODEL_CONFIG_UNAVAILABLE");
            secure(directory, true);
            Path lockPath = directory.resolve("models.lock");
            if (Files.isSymbolicLink(lockPath)) throw fail(503, "MODEL_CONFIG_UNAVAILABLE");
            try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ignored = lockChannel.lock()) {
                secure(lockPath, false);
                Map<String, Object> state = load();
                T result = action.apply(state);
                temporary = Files.createTempFile(directory, "models-", ".tmp");
                secure(temporary, false);
                byte[] bytes = json.writeValueAsBytes(state);
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer data = ByteBuffer.wrap(bytes);
                    while (data.hasRemaining()) channel.write(data);
                    channel.force(true);
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return result;
            }
        } catch (InstallationException error) { throw error; }
        catch (Exception ignored) { throw fail(503, "MODEL_CONFIG_UNAVAILABLE"); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
        }
    }

    private void secure(Path path, boolean isDirectory) throws java.io.IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(isDirectory ? "rwx------" : "rw-------"));
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl != null) {
                var builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class));
                if (isDirectory) builder.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
                acl.setAcl(List.of(builder.build()));
            }
        }
    }

    private InstallationException fail(int status, String code) { return new InstallationException(status, code); }
}
