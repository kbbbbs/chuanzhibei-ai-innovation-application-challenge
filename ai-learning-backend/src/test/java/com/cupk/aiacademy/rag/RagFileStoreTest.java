package com.cupk.aiacademy.rag;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class RagFileStoreTest {
    @TempDir Path temporary;

    @Test
    void writesVerifiesAndDeletesAPrivateOriginalWithoutExposingItsName() throws Exception {
        var store=store();
        store.initialize();store.initialize();
        String key=UUID.randomUUID().toString();
        byte[] contents="private course original".getBytes(StandardCharsets.UTF_8);
        Path path=store.store(key,contents);
        assertEquals(temporary.resolve("private").resolve(key),path);
        assertArrayEquals(contents,Files.readAllBytes(store.verified(key,contents.length,sha256(contents))));
        assertEquals(path,store.path(key));
        store.delete(key);store.delete(key);
        assertFalse(Files.exists(path));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={"../original.pdf","..\\original.pdf","/absolute/file","1-1-1-1-1","not-a-uuid"})
    void rejectsInvalidObjectKeysBeforeAnyFileMutation(String key) throws Exception {
        var store=store();store.initialize();
        assertCode("400",()->store.store(key,new byte[]{1}));
        assertCode("400",()->store.path(key));
        assertCode("400",()->store.delete(key));
        assertEquals(0,countFiles());
    }

    @Test
    void refusesToOverwriteAnExistingObjectAndRemovesItsStagingFile() throws Exception {
        var store=store();store.initialize();
        String key=UUID.randomUUID().toString();
        store.store(key,new byte[]{1,2,3});
        assertCode("409",()->store.store(key,new byte[]{9,8,7}));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(store.path(key)));
        assertEquals(1,countFiles());
    }

    @Test
    void deleteRecoversOnlyThisObjectsValidStagingFilesAfterAnInterruptedWrite() throws Exception {
        var store=store();store.initialize();
        String key=UUID.randomUUID().toString(),other=UUID.randomUUID().toString();
        Path root=temporary.resolve("private");
        store.store(key,new byte[]{1,2,3});
        Path staged=Files.writeString(root.resolve("."+key+"."+UUID.randomUUID()+".partial"),"interrupted bytes");
        Path otherStaged=Files.writeString(root.resolve("."+other+"."+UUID.randomUUID()+".partial"),"other upload");
        Path malformed=Files.writeString(root.resolve("."+key+".not-a-uuid.partial"),"not managed staging");
        store.delete(key);
        assertFalse(Files.exists(root.resolve(key)));
        assertFalse(Files.exists(staged));
        assertEquals("other upload",Files.readString(otherStaged));
        assertEquals("not managed staging",Files.readString(malformed));

        Path unpublished=Files.writeString(root.resolve("."+key+"."+UUID.randomUUID()+".partial"),"not yet published");
        store.delete(key);
        assertFalse(Files.exists(unpublished),"A pre-publication crash has no UUID target but still needs staging recovery");
    }

    @Test
    void aConflictingWriterCleansOnlyItsOwnStagingAndPreservesOtherWritersStaging() throws Exception {
        var store=store();store.initialize();
        String key=UUID.randomUUID().toString();
        store.store(key,new byte[]{1,2,3});
        Path otherWriter=Files.writeString(temporary.resolve("private").resolve("."+key+"."+UUID.randomUUID()+".partial"),"other writer");
        assertCode("409",()->store.store(key,new byte[]{9,8,7}));
        assertEquals("other writer",Files.readString(otherWriter));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(store.path(key)));
        assertEquals(2,countFiles());
    }

    @Test
    void stagingRecoveryRejectsUnexpectedDirectoriesAndPreservesTheirContents() throws Exception {
        var store=store();store.initialize();
        String key=UUID.randomUUID().toString();
        Path directory=Files.createDirectory(temporary.resolve("private").resolve("."+key+"."+UUID.randomUUID()+".partial"));
        Files.writeString(directory.resolve("keep.txt"),"preserve");
        assertCode("409",()->store.delete(key));
        assertEquals("preserve",Files.readString(directory.resolve("keep.txt")));
    }

    @Test
    void stagingRecoveryRejectsSymbolicLinksWithoutTouchingTheirTargets() throws Exception {
        var store=store();store.initialize();
        String key=UUID.randomUUID().toString();
        Path outside=Files.writeString(temporary.resolve("outside-stage.txt"),"outside original");
        Path linked=temporary.resolve("private").resolve("."+key+"."+UUID.randomUUID()+".partial");
        createSymbolicLinkOrSkip(linked,outside);
        assertCode("409",()->store.delete(key));
        assertEquals("outside original",Files.readString(outside));
    }

    @Test
    void concurrentPublishNeverReplacesTheWinningCompleteObject() throws Exception {
        var store=store();store.initialize();
        String key=UUID.randomUUID().toString();
        byte[] first=new byte[256*1024],second=new byte[256*1024];
        java.util.Arrays.fill(first,(byte)1);java.util.Arrays.fill(second,(byte)2);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<String>> calls=List.of(()->publish(store,key,first),()->publish(store,key,second));
            var outcomes=executor.invokeAll(calls).stream().map(future->{
                try{return future.get();}catch(Exception error){throw new AssertionError(error);}
            }).sorted().toList();
            assertEquals(List.of("409","stored"),outcomes);
        }
        byte[] actual=Files.readAllBytes(store.path(key));
        assertTrue(java.util.Arrays.equals(first,actual)||java.util.Arrays.equals(second,actual));
        assertEquals(1,countFiles());
    }

    @Test
    void rejectsEmptyOrOversizedOriginalsWithoutLeavingObjects() throws Exception {
        var store=store();store.initialize();
        String key=UUID.randomUUID().toString();
        assertCode("400",()->store.store(key,null));
        assertCode("400",()->store.store(key,new byte[0]));
        assertCode("400",()->store.store(key,new byte[RagDocumentParser.MAX_BYTES+1]));
        assertEquals(0,countFiles());
    }

    @Test
    void refusesMissingTruncatedTamperedAndInvalidChecksumOriginals() throws Exception {
        var store=store();store.initialize();
        String key=UUID.randomUUID().toString();
        byte[] contents="expected bytes".getBytes(StandardCharsets.UTF_8);
        assertCode("404",()->store.verified(key,contents.length,sha256(contents)));
        store.store(key,contents);
        assertCode("409",()->store.verified(key,contents.length+1,sha256(contents)));
        assertCode("409",()->store.verified(key,contents.length,"0".repeat(64)));
        Files.write(store.path(key),"tampered bytes".getBytes(StandardCharsets.UTF_8));
        assertCode("409",()->store.verified(key,contents.length,sha256(contents)));
    }

    @Test
    void failedStagedWriteIsRemovedWithoutPublishingPartialBytes() throws Exception {
        var store=new RagFileStore(temporary.resolve("private").toString()) {
            @Override void writeStagedFile(Path staged,byte[] contents) throws IOException {
                Files.write(staged,new byte[]{1});
                throw new IOException("private-path-and-provider-details-must-not-escape");
            }
        };
        store.initialize();
        assertCode("500",()->store.store(UUID.randomUUID().toString(),new byte[]{1,2,3}));
        assertEquals(0,countFiles());
    }

    @Test
    void nonFileAtObjectKeyCannotBeSilentlyDeleted() throws Exception {
        var store=store();store.initialize();
        String key=UUID.randomUUID().toString();
        Path directory=Files.createDirectory(temporary.resolve("private").resolve(key));
        Files.writeString(directory.resolve("keep.txt"),"preserve");
        assertCode("409",()->store.delete(key));
        assertTrue(Files.exists(directory.resolve("keep.txt")));
    }

    @Test
    void privateRootMustNotBeInsideConfiguredPublicUploadsOrMedia() {
        for(String publicFolder:List.of("uploads","media")) {
            var store=new RagFileStore(temporary.resolve(publicFolder).resolve("rag").toString(),
                    temporary.resolve("uploads").toString(),temporary.resolve("media").toString());
            var failure=assertThrows(IllegalStateException.class,store::initialize);
            assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains(temporary.toString()));
        }
    }

    @Test
    void rejectsASymbolicLinkAtAnObjectKeyForReadAndDelete() throws Exception {
        var store=store();store.initialize();
        Path outside=Files.writeString(temporary.resolve("outside.txt"),"must remain untouched");
        String key=UUID.randomUUID().toString();
        createSymbolicLinkOrSkip(temporary.resolve("private").resolve(key),outside);
        assertCode("409",()->store.path(key));
        assertCode("409",()->store.verified(key,Files.size(outside),sha256(Files.readAllBytes(outside))));
        assertCode("409",()->store.delete(key));
        assertEquals("must remain untouched",Files.readString(outside));
    }

    @Test
    void refusesASymbolicLinkAsPrivateRootOrItsAncestor() throws Exception {
        Path outside=Files.createDirectory(temporary.resolve("outside"));
        Path linked=temporary.resolve("linked");
        createSymbolicLinkOrSkip(linked,outside);
        assertThrows(IllegalStateException.class,()->new RagFileStore(linked.toString()).initialize());
        assertThrows(IllegalStateException.class,()->new RagFileStore(linked.resolve("nested").toString()).initialize());
        assertFalse(Files.exists(outside.resolve("nested")));
    }

    @Test
    void rechecksRootLinksWhenAReadyDirectoryIsReplaced() throws Exception {
        var store=store();store.initialize();
        Path outside=Files.createDirectory(temporary.resolve("outside"));
        Path root=temporary.resolve("private");Files.delete(root);
        createSymbolicLinkOrSkip(root,outside);
        assertCode("409",()->store.store(UUID.randomUUID().toString(),new byte[]{1}));
        try(var files=Files.list(outside)){assertEquals(0,files.count());}
    }

    private RagFileStore store(){return new RagFileStore(temporary.resolve("private").toString());}
    private long countFiles() throws IOException {try(var files=Files.list(temporary.resolve("private"))){return files.count();}}
    private String publish(RagFileStore store,String key,byte[] bytes) {
        try {store.store(key,bytes);return "stored";}catch(RagAccessException conflict){return conflict.code();}
    }
    private static String sha256(byte[] contents) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
    }
    private static void assertCode(String code,org.junit.jupiter.api.function.Executable run) {
        var failure=assertThrows(RagAccessException.class,run);
        assertEquals(code,failure.code());assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("private-path-and-provider-details"));
    }
    private static void createSymbolicLinkOrSkip(Path link,Path target) throws IOException {
        try {Files.createSymbolicLink(link,target);}
        catch(UnsupportedOperationException|java.nio.file.FileSystemException unavailable) {
            Assumptions.assumeTrue(false,"OS account cannot create symbolic links; guard needs manual verification on this host");
        }
    }
}
