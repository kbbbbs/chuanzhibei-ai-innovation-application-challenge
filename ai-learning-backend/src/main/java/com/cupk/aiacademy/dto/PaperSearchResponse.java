package com.cupk.aiacademy.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class PaperSearchResponse {
    private int total;
    private int offset;
    private List<Paper> data;

    @Data
    @NoArgsConstructor
    public static class Paper {
        private String paperId;
        private String title;
        private Integer year;
        private List<Author> authors;
        private String abstractText;
        private String url;
        private OpenAccessPdf openAccessPdf;
        private String venue;
        private int citationCount;
        private String source;
        private String doi;
        private String publishedDate;
    }

    @Data
    @NoArgsConstructor
    public static class Author {
        private String authorId;
        private String name;
    }

    @Data
    @NoArgsConstructor
    public static class OpenAccessPdf {
        private String url;
        private String status;
    }
}
