package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.PaperSearchResponse;
import com.cupk.aiacademy.entity.Paper;
import com.cupk.aiacademy.entity.PaperAuthor;
import com.cupk.aiacademy.mapper.PaperAuthorMapper;
import com.cupk.aiacademy.mapper.PaperMapper;
import com.cupk.aiacademy.pojo.Course;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PaperRecommendService {

    private static final Logger log = LoggerFactory.getLogger(PaperRecommendService.class);

    private final PaperMapper paperMapper;
    private final PaperAuthorMapper paperAuthorMapper;
    private final OpenAlexSearchService openAlexSearchService;
    private final ArxivSearchService arxivSearchService;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public PaperRecommendService(PaperMapper paperMapper,
                                 PaperAuthorMapper paperAuthorMapper,
                                 OpenAlexSearchService openAlexSearchService,
                                 ArxivSearchService arxivSearchService) {
        this.paperMapper = paperMapper;
        this.paperAuthorMapper = paperAuthorMapper;
        this.openAlexSearchService = openAlexSearchService;
        this.arxivSearchService = arxivSearchService;
    }

    public Result recommend(List<Course> courses, int maxPerCourse, int maxTotal) {
        if (courses == null || courses.isEmpty()) {
            return Result.success(emptyResponse());
        }

        Set<Integer> seenPaperIds = new HashSet<>();
        List<PaperSearchResponse.Paper> results = new ArrayList<>();

        for (Course course : courses) {
            if (results.size() >= maxTotal) break;

            List<String> terms = extractKeywords(course);
            List<Paper> matched = searchPapers(terms, seenPaperIds, maxPerCourse);

            for (Paper p : matched) {
                if (seenPaperIds.add(p.getId())) {
                    results.add(toDto(p));
                    if (results.size() >= maxTotal) break;
                }
            }
        }

        // batch-resolve authors for all local results in one pass
        batchResolveAuthors(results);

        if (results.isEmpty()) {
            results.addAll(searchExternalRecommendations(courses, maxPerCourse, maxTotal));
        }

        PaperSearchResponse response = new PaperSearchResponse();
        response.setTotal(results.size());
        response.setData(results);
        return Result.success(response);
    }

    private List<String> extractKeywords(Course course) {
        List<String> terms = new ArrayList<>();
        String title = (course.getTitle() != null ? course.getTitle() : "") + " " +
                (course.getDescription() != null ? course.getDescription() : "");
        String lower = title.toLowerCase();

        addTerms(terms, lower.contains("java"), "Java", "Spring", "JVM");
        addTerms(terms, lower.contains("python"), "Python", "machine learning");
        addTerms(terms, lower.contains("vue") || lower.contains("前端"), "frontend", "Vue", "component");
        addTerms(terms, lower.contains("mysql") || title.contains("数据库"), "MySQL", "database", "SQL");
        addTerms(terms, title.contains("算法") || title.contains("数据结构"), "algorithm", "sorting", "graph", "data structure");
        addTerms(terms, title.contains("英语") || title.contains("写作") || title.contains("学术"), "academic writing", "English", "citation");
        addTerms(terms, title.contains("操作系统") || lower.contains("os"), "operating system", "Linux", "kernel");
        addTerms(terms, title.contains("网络"), "network", "TCP", "protocol");
        addTerms(terms, title.contains("人工智能") || lower.contains("ai"), "machine learning", "deep learning", "neural network");
        addTerms(terms, title.contains("机器学习"), "machine learning", "supervised learning", "classification");
        addTerms(terms, title.contains("深度学习"), "deep learning", "CNN", "transformer");
        addTerms(terms, title.contains("嵌入") || title.contains("嵌入式"), "embedded system", "IoT", "microcontroller");
        addTerms(terms, title.contains("编译"), "compiler", "parser", "LLVM");

        if (terms.isEmpty() && course.getTitle() != null) {
            for (String word : course.getTitle().split("[\\s，,、]+")) {
                if (word.length() >= 2) terms.add(word);
            }
        }

        return terms;
    }

    private void addTerms(List<String> terms, boolean match, String... values) {
        if (match) {
            Collections.addAll(terms, values);
        }
    }

    private List<PaperSearchResponse.Paper> searchExternalRecommendations(List<Course> courses, int maxPerCourse, int maxTotal) {
        List<PaperSearchResponse.Paper> results = new ArrayList<>();
        Set<String> seenKeys = Collections.synchronizedSet(new HashSet<>());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Course course : courses) {
            List<String> terms = extractKeywords(course);
            String keyword = buildExternalKeyword(course, terms);
            if (keyword == null || keyword.isBlank()) {
                continue;
            }

            // OpenAlex and arXiv in parallel per course
            futures.add(CompletableFuture.runAsync(() -> {
                appendExternalResults(results, seenKeys,
                    openAlexSearchService.search(keyword, 1, maxPerCourse, "relevance"), maxTotal);
            }, executor));

            futures.add(CompletableFuture.runAsync(() -> {
                appendExternalResults(results, seenKeys,
                    arxivSearchService.search(keyword, 1, maxPerCourse, "relevance"), maxTotal);
            }, executor));
        }

        // wait for all external calls with a 12s total timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(12, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("External recommendation search timed out or failed: {}", e.getMessage());
        }

        return results;
    }

    private String buildExternalKeyword(Course course, List<String> terms) {
        if (terms != null && !terms.isEmpty()) {
            return terms.stream().limit(2).collect(Collectors.joining(" "));
        }
        return course.getTitle();
    }

    private List<Paper> searchPapers(List<String> terms, Set<Integer> excludeIds, int limit) {
        if (terms.isEmpty()) return Collections.emptyList();

        QueryWrapper<Paper> wrapper = new QueryWrapper<>();
        wrapper.and(w -> {
            for (int i = 0; i < terms.size() && i < 6; i++) {
                if (i == 0) w.like("title", terms.get(i)).or().like("abstract_text", terms.get(i));
                else w.or().like("title", terms.get(i)).or().like("abstract_text", terms.get(i));
            }
        });
        if (!excludeIds.isEmpty()) {
            wrapper.notIn("id", excludeIds);
        }
        wrapper.orderByDesc("citation_count");
        wrapper.last("LIMIT " + limit);

        return paperMapper.selectList(wrapper);
    }

    private PaperSearchResponse.Paper toDto(Paper entity) {
        PaperSearchResponse.Paper dto = new PaperSearchResponse.Paper();
        dto.setPaperId(entity.getArxivId());
        dto.setTitle(entity.getTitle());
        dto.setYear(entity.getYear());
        dto.setAbstractText(entity.getAbstractText());
        dto.setUrl(entity.getUrl());
        dto.setVenue(entity.getVenue());
        dto.setCitationCount(entity.getCitationCount() != null ? entity.getCitationCount() : 0);
        dto.setSource("local");

        if (entity.getPdfUrl() != null && !entity.getPdfUrl().isEmpty()) {
            PaperSearchResponse.OpenAccessPdf pdf = new PaperSearchResponse.OpenAccessPdf();
            pdf.setUrl(entity.getPdfUrl());
            pdf.setStatus("OA");
            dto.setOpenAccessPdf(pdf);
        }

        // authors filled later by batchResolveAuthors
        dto.setAuthors(Collections.emptyList());
        return dto;
    }

    private void batchResolveAuthors(List<PaperSearchResponse.Paper> results) {
        if (results.isEmpty()) return;

        List<String> arxivIds = results.stream()
            .map(PaperSearchResponse.Paper::getPaperId)
            .filter(id -> id != null && !id.isEmpty())
            .distinct()
            .collect(Collectors.toList());

        if (arxivIds.isEmpty()) return;

        List<Paper> papers = paperMapper.selectList(
            new QueryWrapper<Paper>().in("arxiv_id", arxivIds));

        Map<String, Integer> arxivToPaperId = new HashMap<>();
        for (Paper p : papers) {
            if (p.getArxivId() != null) {
                arxivToPaperId.put(p.getArxivId(), p.getId());
            }
        }

        if (arxivToPaperId.isEmpty()) return;

        List<PaperAuthor> allAuthors = paperAuthorMapper.selectList(
            new QueryWrapper<PaperAuthor>()
                .in("paper_id", new ArrayList<>(arxivToPaperId.values()))
                .orderByAsc("sort_order"));

        Map<Integer, List<PaperSearchResponse.Author>> authorMap = new HashMap<>();
        for (PaperAuthor a : allAuthors) {
            authorMap.computeIfAbsent(a.getPaperId(), k -> new ArrayList<>())
                .add(toAuthorDto(a));
        }

        for (PaperSearchResponse.Paper dto : results) {
            String arxivId = dto.getPaperId();
            if (arxivId != null) {
                Integer paperId = arxivToPaperId.get(arxivId);
                if (paperId != null) {
                    List<PaperSearchResponse.Author> authors = authorMap.get(paperId);
                    if (authors != null && !authors.isEmpty()) {
                        dto.setAuthors(authors);
                    }
                }
            }
        }
    }

    private PaperSearchResponse.Author toAuthorDto(PaperAuthor a) {
        PaperSearchResponse.Author author = new PaperSearchResponse.Author();
        author.setName(a.getName());
        return author;
    }

    private void appendExternalResults(List<PaperSearchResponse.Paper> results,
                                        Set<String> seenKeys,
                                        Result externalResult,
                                        int maxTotal) {
        if (externalResult == null) return;
        if (!"200".equals(externalResult.getCode())) return;
        Object data = externalResult.getData();
        if (!(data instanceof PaperSearchResponse resp)) return;
        List<PaperSearchResponse.Paper> items = resp.getData();
        if (items == null) return;

        synchronized (results) {
            for (PaperSearchResponse.Paper p : items) {
                if (results.size() >= maxTotal) break;
                String key = p.getSource() + ":" + p.getPaperId();
                if (seenKeys.add(key)) {
                    results.add(p);
                }
            }
        }
    }

    private PaperSearchResponse emptyResponse() {
        PaperSearchResponse r = new PaperSearchResponse();
        r.setTotal(0);
        r.setData(Collections.emptyList());
        return r;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
