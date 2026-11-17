package com.cupk.installation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cupk.aiacademy.installation.InstallationException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.Function;

final class InstallationFiles {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final Object MONITOR = new Object();

    private InstallationFiles() {}

    static Path directory(Map<String, String> environment) {
        return Path.of(environment.getOrDefault("AI_INSTALLATION_DIR", "./data/installation"))
                .toAbsolutePath().normalize();
    }

    static void secure(Path path) throws IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(Files.isDirectory(path) ? "rwx------" : "rw-------"));
        } else {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl != null) {
                var principal = path.getFileSystem().getUserPrincipalLookupService()
                        .lookupPrincipalByName(System.getProperty("user.name"));
                acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                        .setPrincipal(principal).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            }
        }
    }

    static void prepare(Path directory) throws IOException {
        Files.createDirectories(directory);
        secure(directory);
    }

    static void write(Path path, Object data) throws IOException {
        prepare(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".installation-", ".tmp");
        try {
            secure(temporary);
            JSON.writeValue(temporary.toFile(), data);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            secure(path);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Map<String, Object> read(Path path) throws IOException {
        if (!Files.exists(path)) return new LinkedHashMap<>();
        return JSON.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    static <T> T locked(Path directory, Function<Map<String, Object>, T> action) {
        synchronized (MONITOR) {
            try {
                prepare(directory);
                Path lockPath = directory.resolve("lifecycle.lock");
                try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                    secure(lockPath);
                    Path ledger = directory.resolve("operations.json");
                    Map<String, Object> data = read(ledger);
                    T result = action.apply(data);
                    write(ledger, data);
                    return result;
                }
            } catch (IOException exception) {
                throw new InstallationException(503, "INSTALLATION_STORAGE_UNAVAILABLE");
            }
        }
    }
}
