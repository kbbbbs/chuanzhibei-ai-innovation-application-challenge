package com.cupk.installation;

import com.cupk.aiacademy.installation.InstallationConfigStore;
import com.cupk.aiacademy.installation.InstallationSessions;
import com.cupk.aiacademy.installation.InstallationStateStore;
import java.io.File;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.MapPropertySource;

/** The only packaged entry point. No shell, Docker socket, or browser-supplied commands. */
public final class InstallationLauncher {
    private InstallationLauncher() {}

    public static void main(String[] arguments) {
        try {
            Map<String, String> environment = System.getenv();
            Path directory = InstallationFiles.directory(environment);
            InstallationFiles.prepare(directory);
            if (arguments.length == 1 && "--installation-code".equals(arguments[0])) {
                System.out.println(InstallationSessions.issueHostCode(directory, "INSTALL"));
                return;
            }
            if (arguments.length == 1 && "--recovery-code".equals(arguments[0])) {
                var lifecycle = new InstallationLifecycle(directory, environment);
                var operation = lifecycle.current();
                if (operation == null || !"RECOVERY_REQUIRED".equals(operation.get("status")))
                    throw new IllegalStateException("RECOVERY_NOT_REQUIRED");
                System.out.println(InstallationSessions.issueHostCode(directory, "RECOVERY",
                        (String) operation.get("operationId")));
                return;
            }
            if (environment.containsKey("AI_INSTALLATION_CHILD")) {
                runChild(directory, environment, arguments);
                return;
            }
            supervise(directory, environment, arguments, Duration.ofSeconds(InstallationLifecycle.READY_SECONDS));
        } catch (Exception failure) {
            System.err.println("Installation launcher failed (" + failureSummary(failure)
                    + "); inspect the private installation state.");
            System.exit(1);
        }
    }

    /** Exception messages can contain connection strings or credentials; only bare error codes are shown. */
    static String failureSummary(Throwable failure) {
        String summary = failure.getClass().getSimpleName();
        String message = failure.getMessage();
        if (message != null && message.matches("[A-Z][A-Z0-9_]{2,63}")) summary += ": " + message;
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        if (root != failure) summary += " (caused by " + root.getClass().getSimpleName() + ")";
        return summary;
    }

    static void supervise(Path directory, Map<String, String> environment, String[] arguments, Duration readyTimeout)
            throws Exception {
        Path lockPath = directory.resolve("launcher.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            InstallationFiles.secure(lockPath);
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException busy) { lock = null; }
            if (lock == null) throw new IllegalStateException("INSTANCE_ALREADY_RUNNING");
            try (FileLock ignored = lock) {
                InstallationLifecycle lifecycle = new InstallationLifecycle(directory, environment);
                InstallationStateStore installation = new InstallationStateStore(directory, environment);
                InstallationConfigStore models = new InstallationConfigStore(directory, environment);
                AtomicReference<Process> childReference = new AtomicReference<>();
                Thread shutdown = new Thread(() -> terminate(childReference.get()), "installation-child-cleanup");
                Runtime.getRuntime().addShutdownHook(shutdown);
                try {
                    var previous = lifecycle.current();
                    if (previous != null && "STARTING".equals(previous.get("status"))) {
                        lifecycle.failed("START_INTERRUPTED");
                    }
                    while (true) {
                        Map<String, Object> operation = lifecycle.claim();
                        String state = lifecycle.state();
                        boolean explicitSetup = setupRequested(arguments) && !installation.installed()
                                && lifecycle.current() == null
                                && Set.of("UNINITIALIZED", "CONFIGURING").contains(state);
                        boolean legacy = !explicitSetup && legacyEnvironment(environment, arguments);
                        if (operation == null && !installation.installed() && !legacy
                                && "READY".equals(lifecycle.state())) {
                            lifecycle.failed("LEGACY_CONFIGURATION_MISSING");
                        }
                        boolean recovery = "RECOVERY_REQUIRED".equals(lifecycle.state());
                        boolean app = operation != null || (!recovery
                                && (installation.installed() || legacy));
                        String revision = operation == null
                                ? Objects.toString(models.readModels().get("activeRevision"), "environment")
                                : (String) operation.get("targetRevision");
                        String nonce = UUID.randomUUID().toString();
                        Path readyPath = directory.resolve("ready-" + nonce + ".json");
                        Process child = startChild(directory, environment, arguments, app, revision,
                                operation == null ? "" : (String) operation.get("operationId"), nonce);
                        childReference.set(child);
                        boolean ready = waitReady(child, readyPath, nonce, lifecycle.installationId(), revision,
                                readyTimeout);
                        Files.deleteIfExists(readyPath);
                        if (!ready) {
                            boolean timedOut = child.isAlive();
                            terminate(child);
                            childReference.compareAndSet(child, null);
                            if (!app) throw new IllegalStateException("BOOTSTRAP_START_FAILED");
                            lifecycle.failed(timedOut ? "START_TIMEOUT" : "START_FAILED");
                            continue;
                        }
                        if (operation != null) {
                            try {
                                lifecycle.applied((String) operation.get("operationId"),
                                        lifecycle.installationId(), revision);
                            } catch (RuntimeException failure) {
                                lifecycle.failed("ACTIVATION_FAILED");
                                terminate(child);
                                childReference.compareAndSet(child, null);
                                continue;
                            }
                        } else if (app) lifecycle.confirmed(revision);
                        int exit = child.waitFor();
                        childReference.compareAndSet(child, null);
                        if (exit == InstallationLifecycle.APPLY_EXIT_CODE) {
                            var pending = lifecycle.current();
                            if (pending != null && Set.of("ACCEPTED", "DRAINING").contains(pending.get("status")))
                                continue;
                            throw new IllegalStateException("UNAUTHORIZED_RESTART");
                        }
                        if (!app) {
                            if (exit != 0) throw new IllegalStateException("BOOTSTRAP_EXITED");
                            return;
                        }
                        if (exit == 0) return;
                        lifecycle.failed("APPLICATION_EXITED");
                    }
                } finally {
                    terminate(childReference.get());
                    try { Runtime.getRuntime().removeShutdownHook(shutdown); }
                    catch (IllegalStateException shuttingDown) { /* VM shutdown owns the hook. */ }
                }
            }
        }
    }

    private static Process startChild(Path directory, Map<String, String> environment, String[] arguments, boolean app,
                                      String revision, String operation, String nonce) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString());
        File source = new ApplicationHome(InstallationLauncher.class).getSource();
        if (source != null && source.isFile() && source.getName().endsWith(".jar")) {
            command.add("-jar");
            command.add(source.getAbsolutePath());
        } else {
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(InstallationLauncher.class.getName());
        }
        Arrays.stream(arguments).filter(argument -> !"--installation-setup".equals(argument)).forEach(command::add);
        ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
        builder.environment().clear();
        builder.environment().putAll(environment);
        builder.environment().put("AI_INSTALLATION_DIR", directory.toString());
        builder.environment().put("AI_INSTALLATION_CHILD", app ? "APP" : "BOOTSTRAP");
        builder.environment().put("AI_INSTALLATION_REVISION", revision);
        builder.environment().put("AI_INSTALLATION_OPERATION", operation);
        builder.environment().put("AI_INSTALLATION_HANDSHAKE", nonce);
        builder.environment().put("AI_INSTALLATION_PARENT_PID", Long.toString(ProcessHandle.current().pid()));
        builder.environment().put("AI_INSTALLATION_PARENT_STARTED", ProcessHandle.current().info().startInstant()
                .orElseThrow(() -> new IllegalStateException("PARENT_IDENTITY_UNAVAILABLE")).toString());
        return builder.start();
    }

    private static void runChild(Path directory, Map<String, String> environment, String[] arguments)
            throws Exception {
        AtomicReference<ConfigurableApplicationContext> contextReference = new AtomicReference<>();
        watchParent(environment, contextReference);
        Path workerLock = directory.resolve("worker.lock");
        try (FileChannel channel = FileChannel.open(workerLock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            InstallationFiles.secure(workerLock);
            runLockedChild(directory, environment, arguments, contextReference);
        }
    }

    private static void runLockedChild(Path directory, Map<String, String> environment, String[] arguments,
                                       AtomicReference<ConfigurableApplicationContext> contextReference) throws Exception {
        boolean app = "APP".equals(environment.get("AI_INSTALLATION_CHILD"));
        String revision = environment.get("AI_INSTALLATION_REVISION");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("installation.lifecycle.enabled", true);
        properties.put("server.shutdown", "graceful");
        properties.put("spring.lifecycle.timeout-per-shutdown-phase", "30s");
        if (app) {
            var installation = new InstallationStateStore(directory, environment);
            if (installation.installed()) properties.putAll(installation.workerProperties());
            if (!"environment".equals(revision))
                properties.putAll(new InstallationConfigStore(directory, environment).exportProperties(revision));
        }
        Class<?> source = Class.forName(app ? "com.cupk.aiacademy.AiAcademyApplication"
                : "com.cupk.installation.BootstrapApplication");
        SpringApplication application = new SpringApplication(source);
        application.addInitializers(context -> {
            var sources = context.getEnvironment().getPropertySources();
            MapPropertySource privateProperties = new MapPropertySource("installationPrivate", properties);
            if (sources.contains("systemEnvironment")) sources.addAfter("systemEnvironment", privateProperties);
            else sources.addFirst(privateProperties);
        });
        CountDownLatch closed = new CountDownLatch(1);
        application.addListeners(event -> {
            if (event instanceof ContextClosedEvent) closed.countDown();
        });
        var context = application.run(arguments);
        contextReference.set(context);
        String nonce = environment.get("AI_INSTALLATION_HANDSHAKE");
        if (nonce == null || !nonce.matches("[0-9a-f-]{36}"))
            throw new IllegalStateException("INVALID_HANDSHAKE");
        InstallationFiles.write(directory.resolve("ready-" + nonce + ".json"), Map.of(
                "nonce", nonce,
                "installationId", new InstallationStateStore(directory, environment).installationId(),
                "revision", revision));
        closed.await();
    }

    static boolean waitReady(Process child, Path file, String nonce, String instance,
                                     String revision, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (child.isAlive() && System.nanoTime() < deadline) {
            if (Files.exists(file)) {
                var ready = InstallationFiles.read(file);
                return nonce.equals(ready.get("nonce")) && instance.equals(ready.get("installationId"))
                        && revision.equals(ready.get("revision"));
            }
            child.waitFor(100, TimeUnit.MILLISECONDS);
        }
        return false;
    }

    private static void watchParent(Map<String, String> environment,
                                    AtomicReference<ConfigurableApplicationContext> contextReference) {
        long parentPid = Long.parseLong(environment.get("AI_INSTALLATION_PARENT_PID"));
        String parentStarted = Objects.requireNonNull(environment.get("AI_INSTALLATION_PARENT_STARTED"));
        Thread monitor = new Thread(() -> {
            while (true) {
                boolean alive = ProcessHandle.of(parentPid).filter(ProcessHandle::isAlive)
                        .flatMap(parent -> parent.info().startInstant())
                        .map(start -> parentStarted.equals(start.toString())).orElse(false);
                if (!alive) {
                    Thread deadline = new Thread(() -> {
                        try { Thread.sleep(InstallationLifecycle.DRAIN_SECONDS * 1000); }
                        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                        Runtime.getRuntime().halt(1);
                    }, "installation-orphan-deadline");
                    deadline.setDaemon(true);
                    deadline.start();
                    var context = contextReference.get();
                    if (context != null) context.close();
                    Runtime.getRuntime().halt(1);
                }
                try { Thread.sleep(500); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }, "installation-parent-watch");
        monitor.setDaemon(true);
        monitor.start();
    }

    private static boolean legacyEnvironment(Map<String, String> environment, String[] arguments) {
        return environment.containsKey("DB_URL") || environment.containsKey("SPRING_DATASOURCE_URL")
                || environment.containsKey("DB_USERNAME") || environment.containsKey("DB_PASSWORD")
                || Arrays.stream(arguments).anyMatch(argument -> argument.startsWith("--spring.profiles.active=")
                    || argument.startsWith("--spring.datasource.url="))
                || environment.containsKey("SPRING_PROFILES_ACTIVE");
    }

    private static boolean setupRequested(String[] arguments) {
        return Arrays.asList(arguments).contains("--installation-setup");
    }

    private static void terminate(Process child) {
        if (child == null || !child.isAlive()) return;
        var descendants = child.descendants().toList();
        child.destroy();
        try {
            if (!child.waitFor(InstallationLifecycle.DRAIN_SECONDS, TimeUnit.SECONDS)) child.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            child.destroyForcibly();
        } finally {
            descendants.forEach(process -> { if (process.isAlive()) process.destroyForcibly(); });
        }
    }
}
