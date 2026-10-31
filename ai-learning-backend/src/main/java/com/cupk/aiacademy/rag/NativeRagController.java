package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.Result;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/rag")
public class NativeRagController {
    private final NativeRagService service;
    public NativeRagController(NativeRagService service) { this.service = service; }
    public record BaseForm(String name, String description, Boolean shared) { }
    public record GrantForm(Integer courseId,int documentVersion,boolean readExcerpt,boolean downloadAllowed) { }

    @GetMapping("/knowledge-bases")
    public Result bases(@RequestParam(defaultValue = "all") String scope) { return service.listKnowledgeBases(scope); }

    @PostMapping("/knowledge-bases")
    public Result create(@RequestBody BaseForm form) { return service.createKnowledgeBase(form.name(), form.description()); }

    @GetMapping("/knowledge-bases/{id}")
    public Result base(@PathVariable String id) { return service.getKnowledgeBase(id); }

    @PutMapping("/knowledge-bases/{id}")
    public Result update(@PathVariable String id, @RequestBody BaseForm form) {
        return service.updateKnowledgeBase(id, form.name(), form.description(), form.shared());
    }

    @DeleteMapping("/knowledge-bases/{id}")
    public Result deleteBase(@PathVariable String id) { return service.deleteKnowledgeBase(id); }

    @GetMapping("/knowledge-bases/{id}/documents")
    public Result documents(@PathVariable String id, @RequestParam(defaultValue = "100") int limit,
                            @RequestParam(defaultValue = "0") int offset) { return service.listKnowledge(id, limit, offset); }

    @PostMapping(value = "/knowledge-bases/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result upload(@PathVariable String id, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > RagDocumentParser.MAX_BYTES) return Result.error("400", "文件不能为空且不能超过 20 MB");
        return service.uploadFile(id, file.getBytes(), file.getOriginalFilename());
    }

    @GetMapping("/documents/{id}")
    public Result document(@PathVariable String id) { return service.getDocument(id); }

    @DeleteMapping("/documents/{id}")
    public Result deleteDocument(@PathVariable String id) { return service.deleteDocument(id); }

    @PutMapping(value="/documents/{id}/file",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result replaceDocument(@PathVariable String id,@RequestParam("file") MultipartFile file) throws IOException {
        if(file.isEmpty() || file.getSize()>RagDocumentParser.MAX_BYTES)return Result.error("400","文件不能为空且不能超过 20 MB");
        return service.replaceDocument(id,file.getBytes(),file.getOriginalFilename());
    }

    @PostMapping("/documents/{id}/grants")
    public Result grant(@PathVariable String id,@RequestBody GrantForm form) {
        return service.grantDocument(id,form.courseId(),form.documentVersion(),form.readExcerpt(),form.downloadAllowed());
    }

    @DeleteMapping("/documents/{id}/grants/{courseId}/versions/{version}")
    public Result revoke(@PathVariable String id,@PathVariable Integer courseId,@PathVariable int version) {
        return service.revokeDocumentGrant(id,courseId,version);
    }

    @GetMapping("/courses/{courseId}/documents/{id}/versions/{version}")
    public Result courseContent(@PathVariable Integer courseId,@PathVariable String id,@PathVariable int version) {
        return service.courseContent(courseId,id,version);
    }

    @GetMapping("/courses/{courseId}/documents/{id}/versions/{version}/download")
    public ResponseEntity<Resource> courseDownload(@PathVariable Integer courseId,@PathVariable String id,@PathVariable int version) {
        return downloadResponse(service.courseOriginalFile(courseId,id,version));
    }

    @PostMapping("/documents/{id}/reindex")
    public Result reindexDocument(@PathVariable String id) { return service.reindexDocument(id); }

    @GetMapping("/documents/{id}/chunks")
    public Result chunks(@PathVariable String id) { return service.chunks(id); }

    @GetMapping("/chunks/{id}")
    public Result chunk(@PathVariable String id) { return service.chunk(id); }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        return downloadResponse(service.originalFile(id));
    }

    private ResponseEntity<Resource> downloadResponse(NativeRagService.OriginalFile source) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(source.mimeType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(source.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl("private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(source.path()));
    }

    @PostMapping("/search")
    public Result search(@RequestBody RagSearchRequest request) { return service.search(request); }

    @ExceptionHandler(RagAccessException.class)
    public ResponseEntity<Result> accessFailure(RagAccessException failure) {
        return ResponseEntity.status(Integer.parseInt(failure.code())).body(Result.error(failure.code(), failure.getMessage()));
    }
}
