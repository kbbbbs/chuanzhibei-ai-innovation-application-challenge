package com.cupk.aiacademy.rag;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Private immutable originals. Authorization and durable deletion retries belong to the application layer. */
@Component
public class RagFileStore {
    private final Path root;
    private final List<Path> publicRoots;

    @Autowired
    public RagFileStore(@Value("${rag.storage-path:./data/native-rag}") String storagePath,
                        @Value("${app.upload.dir:uploads}") String uploadPath,
                        @Value("${app.media.dir:media}") String mediaPath) {
        try {
            if (storagePath == null || storagePath.isBlank()) throw new IllegalArgumentException();
            this.root=Path.of(storagePath).toAbsolutePath().normalize();
            var publiclyServed=new ArrayList<Path>();
            publiclyServed.add(Path.of(uploadPath).toAbsolutePath().normalize());
            publiclyServed.add(Path.of(mediaPath).toAbsolutePath().normalize());
            Path working=Path.of("").toAbsolutePath().normalize();
            publiclyServed.add(working.resolve("ai-learning-ytdlp-service/media"));
            if(working.getParent()!=null) publiclyServed.add(working.getParent().resolve("ai-learning-ytdlp-service/media"));
            this.publicRoots=List.copyOf(publiclyServed);
        } catch(RuntimeException invalid) {
            throw new IllegalStateException("Private RAG storage configuration is invalid");
        }
    }

    public RagFileStore(String storagePath) { this(storagePath,"uploads","media"); }

    @PostConstruct
    public void initialize() {
        try {
            validateAncestors();
            requirePrivateRoot();
            Files.createDirectories(root);
            validateAncestors();
            requirePrivateRoot();
        } catch(Exception invalid) {
            throw new IllegalStateException("Private RAG storage must be a safe directory outside public resources");
        }
    }

    /** Publishes a complete staged inode with createLink's atomic no-replacement guarantee. */
    public Path store(String key,byte[] bytes) {
        Path target=path(key);
        if(bytes==null || bytes.length==0 || bytes.length>RagDocumentParser.MAX_BYTES)
            throw failure("400","Original file must contain at most 20 MB");
        Path staged=root.resolve("."+key+"."+UUID.randomUUID()+".partial");
        boolean published=false;
        try {
            writeStagedFile(staged,bytes);
            checkReadyRoot();
            // ATOMIC_MOVE may replace an existing target on some file systems, even without REPLACE_EXISTING.
            // A same-directory hard link publishes the completed file only when the UUID target is absent.
            Files.createLink(target,staged);
            published=true;
            Files.delete(staged);
            return target;
        } catch(FileAlreadyExistsException conflict) {
            throw failure("409","Original file already exists");
        } catch(Exception invalid) {
            if(published) removeFailedObject(target);
            throw failure("500","Original file storage failed");
        } finally {
            try {Files.deleteIfExists(staged);}
            catch(IOException cleanupFailure) {
                // Never convert a failed cleanup into a successful write. The caller can record a recovery task.
                if(published) removeFailedObject(target);
                throw failure("500","Original file staging cleanup failed");
            }
        }
    }

    /** Package-private seam permits an actual partial-write failure without platform permission assumptions. */
    void writeStagedFile(Path staged,byte[] bytes) throws IOException {
        try(var channel=FileChannel.open(staged,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            var buffer=ByteBuffer.wrap(bytes);
            while(buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    public Path verified(String key,long expectedSize,String expectedSha256) {
        Path target=path(key);
        if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)) throw failure("404","Original file was not found");
        if(expectedSize<=0 || expectedSize>RagDocumentParser.MAX_BYTES || expectedSha256==null
                || !expectedSha256.matches("[0-9a-f]{64}"))
            throw failure("409","Original file verification failed");
        try {
            if(Files.size(target)!=expectedSize) throw failure("409","Original file verification failed");
            var digest=MessageDigest.getInstance("SHA-256");
            long count=0;
            try(var input=Files.newInputStream(target,LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer=new byte[64*1024];
                int read;
                while((read=input.read(buffer))!=-1) {
                    count+=read;
                    if(count>RagDocumentParser.MAX_BYTES) throw failure("409","Original file verification failed");
                    digest.update(buffer,0,read);
                }
            }
            if(count!=expectedSize || !HexFormat.of().formatHex(digest.digest()).equals(expectedSha256))
                throw failure("409","Original file verification failed");
            return target;
        } catch(RagAccessException expected) {throw expected;}
        catch(Exception invalid) {throw failure("500","Original file could not be read");}
    }

    /** Missing files are already deleted; a failed deletion remains an explicit retryable application error. */
    public void delete(String key) {
        Path target=path(key);
        String prefix="."+key+".";
        try(var entries=Files.newDirectoryStream(root,prefix+"*.partial")) {
            var staging=new ArrayList<Path>();
            for(Path entry:entries) {
                String name=entry.getFileName().toString();
                String writer=name.substring(prefix.length(),name.length()-".partial".length());
                if(!canonicalUuid(writer)) continue;
                requireSafeStaging(entry);
                staging.add(entry);
            }
            checkReadyRoot();
            for(Path entry:staging) {
                requireSafeStaging(entry);
                Files.deleteIfExists(entry);
            }
            Files.deleteIfExists(target);
        } catch(IOException | java.nio.file.DirectoryIteratorException invalid) {
            throw failure("500","Original file deletion failed");
        }
    }

    public Path path(String key) {
        try {
            if(key==null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException();
        } catch(IllegalArgumentException invalid) {throw failure("400","Invalid original file identifier");}
        checkReadyRoot();
        Path target=root.resolve(key);
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(target)
                || !Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)))
            throw failure("409","Original file object is invalid");
        return target;
    }

    private void checkReadyRoot() {
        try {
            validateAncestors();
            requirePrivateRoot();
            if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)) throw new IOException();
        } catch(Exception invalid) {throw failure("409","Private original storage is unavailable");}
    }

    private boolean canonicalUuid(String value) {
        try {return value!=null && UUID.fromString(value).toString().equals(value);}
        catch(IllegalArgumentException invalid) {return false;}
    }

    private void requireSafeStaging(Path entry) {
        if(!entry.getParent().equals(root) || Files.exists(entry,LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(entry) || !Files.isRegularFile(entry,LinkOption.NOFOLLOW_LINKS)))
            throw failure("409","Original file staging object is invalid");
    }

    private void validateAncestors() throws IOException {
        Path current=root.getRoot();
        for(Path component:root) {
            current=current.resolve(component);
            if(Files.exists(current,LinkOption.NOFOLLOW_LINKS)) {
                if(Files.isSymbolicLink(current) || !Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS)
                        || !current.toRealPath().equals(current.toAbsolutePath().normalize()))
                    throw new IOException();
            }
        }
    }

    private void requirePrivateRoot() throws IOException {
        Path canonicalRoot=canonicalPath(root);
        for(Path publiclyServed:publicRoots) {
            if(root.startsWith(publiclyServed) || canonicalRoot.startsWith(canonicalPath(publiclyServed)))
                throw new IOException();
        }
    }

    /** Resolve existing ancestors so a configured public symlink cannot alias the private directory. */
    private Path canonicalPath(Path path) throws IOException {
        Path existing=path;
        while(existing!=null && !Files.exists(existing,LinkOption.NOFOLLOW_LINKS)) existing=existing.getParent();
        if(existing==null) throw new IOException();
        return existing.toRealPath().resolve(existing.relativize(path)).normalize();
    }

    private void removeFailedObject(Path object) {
        try {Files.deleteIfExists(object);}
        catch(IOException invalid) {throw failure("500","Original file cleanup failed");}
    }

    private RagAccessException failure(String code,String message) {return new RagAccessException(code,message);}
}
