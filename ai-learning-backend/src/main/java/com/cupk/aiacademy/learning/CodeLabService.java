package com.cupk.aiacademy.learning;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class CodeLabService {
    private static final int MAX_CODE_CHARS = 12000;
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    public CodeLabRunResponse run(CodeLabRunRequest request) {
        if (request == null || isBlank(request.getLanguage()) || isBlank(request.getCode())) {
            throw new IllegalArgumentException("请选择语言并输入代码");
        }
        if (request.getCode().length() > MAX_CODE_CHARS) {
            throw new IllegalArgumentException("代码太长，请控制在 12000 字以内");
        }
        String language = request.getLanguage().trim().toLowerCase(Locale.ROOT);
        try {
            return switch (language) {
                case "java" -> runJava(request.getCode());
                case "python" -> runScript("main.py", request.getCode(), List.of("python", "main.py"));
                case "javascript", "js" -> runScript("main.js", request.getCode(), List.of("node", "main.js"));
                default -> throw new IllegalArgumentException("暂不支持该语言: " + request.getLanguage());
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("代码运行被中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("代码运行环境不可用，请确认本机已安装 Java/Python/Node", e);
        }
    }

    private CodeLabRunResponse runJava(String code) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("amazing-code-lab-");
        try {
            Files.writeString(dir.resolve("Main.java"), code, StandardCharsets.UTF_8);
            CodeLabRunResponse compile = runProcess(List.of("javac", "Main.java"), dir);
            if (compile.getExitCode() != 0 || compile.isTimedOut()) {
                return compile;
            }
            return runProcess(List.of("java", "-cp", ".", "Main"), dir);
        } finally {
            deleteDir(dir);
        }
    }

    private CodeLabRunResponse runScript(String fileName, String code, List<String> command) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("amazing-code-lab-");
        try {
            Files.writeString(dir.resolve(fileName), code, StandardCharsets.UTF_8);
            return runProcess(command, dir);
        } finally {
            deleteDir(dir);
        }
    }

    private CodeLabRunResponse runProcess(List<String> command, Path dir) throws IOException, InterruptedException {
        // Local teaching runtime: bounded temp directory + timeout. Public deployments should run this worker inside an isolated container sandbox.
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(false).start();
        boolean finished = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        CodeLabRunResponse response = new CodeLabRunResponse();
        response.setTimedOut(!finished);
        response.setExitCode(finished ? process.exitValue() : -1);
        response.setStdout(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        response.setStderr((!finished ? "运行超时：请检查是否存在死循环\n" : "")
                + new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        return response;
    }

    private void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // temporary files can be left for OS cleanup
                }
            });
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
