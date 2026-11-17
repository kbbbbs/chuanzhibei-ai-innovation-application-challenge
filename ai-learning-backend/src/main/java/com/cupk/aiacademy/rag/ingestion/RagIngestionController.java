package com.cupk.aiacademy.rag.ingestion;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.rag.RagAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagIngestionController {
    private final RagIngestionService service;
    public RagIngestionController(RagIngestionService service){this.service=service;}
    public record RetryRequest(List<Integer> pages,boolean forceOcr) { }
    public record ReviewRequest(int version,int parseRevision,String text) { }
    @PostMapping("/documents/{id}/pages/{page}/review") public Result review(@PathVariable String id,@PathVariable int page,@RequestBody ReviewRequest request){
        return service.review(id,page,request.version(),request.parseRevision(),request.text());
    }
    @GetMapping("/jobs/{id}") public Result status(@PathVariable String id){return service.status(id);}
    @PostMapping("/jobs/{id}/cancel") public Result cancel(@PathVariable String id){return service.cancel(id);}
    @PostMapping("/documents/{id}/parse-retry") public Result retry(@PathVariable String id,@RequestBody(required=false) RetryRequest request){
        return service.retry(id,request==null?List.of():request.pages(),request!=null&&request.forceOcr());
    }
    @GetMapping("/documents/{id}/pages") public Result pages(@PathVariable String id,@RequestParam(required=false) Integer version){return service.pages(id,version);}
    @GetMapping("/documents/{id}/pages/{page}/preview") public ResponseEntity<byte[]> preview(@PathVariable String id,@PathVariable int page,@RequestParam(required=false) Integer version){
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header("X-Content-Type-Options","nosniff").body(service.preview(id,page,version));
    }
    @ExceptionHandler(RagAccessException.class) public ResponseEntity<Result> denied(RagAccessException error){
        return ResponseEntity.status(Integer.parseInt(error.code())).body(Result.error(error.code(),error.getMessage()));
    }
}
