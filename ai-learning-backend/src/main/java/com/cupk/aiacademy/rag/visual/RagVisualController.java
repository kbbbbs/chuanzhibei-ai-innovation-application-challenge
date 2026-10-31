package com.cupk.aiacademy.rag.visual;

import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.common.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/rag/visuals")
public class RagVisualController {
    private final RagVisualService service;
    private final RagAuthorizationService auth;
    public RagVisualController(RagVisualService service,RagAuthorizationService auth){this.service=service;this.auth=auth;}
    @GetMapping("/{id}/image") public ResponseEntity<byte[]> image(@PathVariable String id){
        byte[] image=service.imageForActor(id,auth.currentActor().userId());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header("X-Content-Type-Options","nosniff").body(image);
    }
    @ExceptionHandler(RagAccessException.class) public ResponseEntity<Result> denied(RagAccessException error){return ResponseEntity.status(Integer.parseInt(error.code())).body(Result.error(error.code(),error.getMessage()));}
}
