package com.cupk.aiacademy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.PaperSearchResponse;
import com.cupk.aiacademy.entity.Paper;
import com.cupk.aiacademy.entity.PaperAuthor;
import com.cupk.aiacademy.mapper.PaperAuthorMapper;
import com.cupk.aiacademy.mapper.PaperMapper;
import com.cupk.aiacademy.service.PaperLocalService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PaperLocalServiceImpl implements PaperLocalService {

    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final String ARXIV_NS = "http://arxiv.org/schemas/atom";
    private static final String ARXIV_API = "https://export.arxiv.org/api/query";
    private static final String USER_AGENT = "AI-Academy/1.0 (mailto:2024015839@cupk.edu.cn)";

    @Autowired
    private PaperMapper paperMapper;

    @Autowired
    private PaperAuthorMapper paperAuthorMapper;

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public Result search(String keyword, Integer page, Integer size,
                         String author, String venue,
                         Integer yearFrom, Integer yearTo,
                         Integer minCitations, String sortBy,
                         Boolean openAccessOnly) {
        try {
            String trimmedKeyword = keyword == null ? "" : keyword.trim();
            String safe = sanitizeKeyword(trimmedKeyword);
            int safePage = page == null || page < 1 ? 1 : page;
            int safeSize = size == null || size < 1 ? 10 : size;

            QueryWrapper<Paper> wrapper = buildBaseQuery(trimmedKeyword, safe, author, venue, yearFrom, yearTo, minCitations, openAccessOnly, true);
            applySort(wrapper, sortBy);

            Page<Paper> pageParam = new Page<>(safePage, safeSize);
            Page<Paper> pageResult = paperMapper.selectPage(pageParam, wrapper);

            if (pageResult.getTotal() == 0) {
                QueryWrapper<Paper> fallback = buildBaseQuery(trimmedKeyword, safe, author, venue, yearFrom, yearTo, minCitations, openAccessOnly, false);
                applySort(fallback, sortBy);
                pageResult = paperMapper.selectPage(pageParam, fallback);
            }

            List<PaperSearchResponse.Paper> papers = pageResult.getRecords().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

            PaperSearchResponse response = new PaperSearchResponse();
            response.setTotal((int) pageResult.getTotal());
            response.setData(papers);
            response.setOffset((safePage - 1) * safeSize);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error("本地论文搜索失败: " + e.getMessage());
        }
    }

    @Override
    public Result getDetail(String paperId) {
        try {
            Paper paper = paperMapper.selectOne(new QueryWrapper<Paper>().eq("arxiv_id", paperId).last("LIMIT 1"));
            if (paper == null) {
                return Result.error("论文不存在");
            }
            return Result.success(toDto(paper));
        } catch (Exception e) {
            return Result.error("获取论文详情失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result importFromArxiv(String keyword, Integer maxResults) {
        try {
            String q = keyword == null ? "" : keyword.trim();
            if (q.isBlank()) {
                return Result.success(0);
            }

            int capped = Math.min(Math.max(maxResults == null ? 20 : maxResults, 1), 100);
            String url = ARXIV_API
                + "?search_query=all:" + java.net.URLEncoder.encode(q, "UTF-8")
                + "&start=0&max_results=" + capped
                + "&sortBy=relevance";

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 429) {
                    return Result.error("429", "arXiv 请求过于频繁，请稍后重试后再导入，或先切换到 OpenAlex / Semantic Scholar 搜索");
                }
                if (!response.isSuccessful()) {
                    return Result.error("arXiv API returned " + response.code());
                }
                if (response.body() == null) {
                    return Result.error("arXiv API returned empty body");
                }

                String body = response.body().string();
                List<ArxivEntry> entries = parseArxivResponse(body);
                int imported = 0;

                for (ArxivEntry entry : entries) {
                    Paper paper = entry.paper;
                    Paper existing = paperMapper.selectOne(
                        new QueryWrapper<Paper>().eq("arxiv_id", paper.getArxivId()));
                    if (existing != null) continue;

                    paperMapper.insert(paper);
                    for (PaperAuthor author : entry.authors) {
                        author.setPaperId(paper.getId());
                        paperAuthorMapper.insert(author);
                    }
                    imported++;
                }

                return Result.success(imported);
            }
        } catch (Exception e) {
            return Result.error("导入论文失败: " + e.getMessage());
        }
    }

    @Override
    public Result getCourseDirections(String keyword) {
        try {
            List<Paper> papers = directKeywordSearch(keyword);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("paperCount", papers.size());

            Map<String, Long> venueCounts = papers.stream()
                .filter(p -> p.getVenue() != null && !p.getVenue().isEmpty())
                .collect(Collectors.groupingBy(Paper::getVenue, Collectors.counting()));
            List<String> topVenues = venueCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
            result.put("directions", topVenues);

            List<String> sampleTitles = papers.stream()
                .limit(5)
                .map(Paper::getTitle)
                .toList();
            result.put("sampleTitles", sampleTitles);

            if (!papers.isEmpty()) {
                int minYear = papers.stream().mapToInt(p -> p.getYear() != null ? p.getYear() : Integer.MAX_VALUE).min().orElse(0);
                int maxYear = papers.stream().mapToInt(p -> p.getYear() != null ? p.getYear() : 0).max().orElse(0);
                result.put("yearRange", minYear + "-" + maxYear);
            }

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取论文方向失败: " + e.getMessage());
        }
    }

    private static final Map<String, String[]> COURSE_TERMS = new LinkedHashMap<>();
    static {
        COURSE_TERMS.put("java",    new String[]{"Java", "Spring", "concurrency", "JVM"});
        COURSE_TERMS.put("vue",     new String[]{"Vue", "frontend", "component", "reactive"});
        COURSE_TERMS.put("mysql",   new String[]{"MySQL", "database", "SQL", "index", "query"});
        COURSE_TERMS.put("数据库",   new String[]{"MySQL", "database", "SQL", "index"});
        COURSE_TERMS.put("算法",     new String[]{"algorithm", "sorting", "graph", "hash"});
        COURSE_TERMS.put("数据结构", new String[]{"data structure", "algorithm", "linked", "graph"});
        COURSE_TERMS.put("英语",     new String[]{"academic", "writing", "English", "citation"});
        COURSE_TERMS.put("学术",     new String[]{"academic", "discourse", "citation"});
        COURSE_TERMS.put("写作",     new String[]{"writing", "academic", "rhetoric"});
        COURSE_TERMS.put("前端",     new String[]{"frontend", "Vue", "component"});
        COURSE_TERMS.put("后端",     new String[]{"Java", "Spring", "database"});
    }

    private List<Paper> directKeywordSearch(String keyword) {
        List<Paper> merged = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();

        if (keyword == null || keyword.isBlank()) return merged;

        String lower = keyword.toLowerCase(Locale.ROOT);

        for (String part : keyword.split("[^A-Za-z0-9]+")) {
            if (part.length() >= 2) searchAndMerge(part, merged, seen);
        }

        for (Map.Entry<String, String[]> entry : COURSE_TERMS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                for (String term : entry.getValue()) {
                    searchAndMerge(term, merged, seen);
                }
            }
        }

        if (merged.size() < 5) {
            for (String term : new String[]{"algorithm", "database", "Vue", "Java", "academic", "frontend"}) {
                searchAndMerge(term, merged, seen);
            }
        }

        return merged;
    }

    private void searchAndMerge(String term, List<Paper> merged, Set<Integer> seen) {
        if (term == null || term.length() < 2) return;
        QueryWrapper<Paper> w = new QueryWrapper<>();
        w.like("title", term).or().like("abstract_text", term);
        w.orderByDesc("citation_count");
        w.last("LIMIT 10");
        for (Paper p : paperMapper.selectList(w)) {
            if (seen.add(p.getId())) merged.add(p);
        }
    }

    private QueryWrapper<Paper> buildBaseQuery(String keyword, String safeKeyword,
                                               String author, String venue,
                                               Integer yearFrom, Integer yearTo,
                                               Integer minCitations, Boolean openAccessOnly,
                                               boolean useFullText) {
        QueryWrapper<Paper> wrapper = new QueryWrapper<>();
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasKeyword) {
            if (useFullText && safeKeyword != null && !safeKeyword.isBlank()) {
                wrapper.apply("MATCH(title, abstract_text) AGAINST({0} IN BOOLEAN MODE)", safeKeyword);
            } else {
                wrapper.and(q -> q.like("title", keyword).or().like("abstract_text", keyword));
            }
        }

        if (venue != null && !venue.isBlank()) {
            wrapper.like("venue", venue.trim());
        }
        if (yearFrom != null) {
            wrapper.ge("year", yearFrom);
        }
        if (yearTo != null) {
            wrapper.le("year", yearTo);
        }
        if (minCitations != null) {
            wrapper.ge("citation_count", minCitations);
        }
        if (Boolean.TRUE.equals(openAccessOnly)) {
            wrapper.isNotNull("pdf_url").ne("pdf_url", "");
        }
        if (author != null && !author.isBlank()) {
            wrapper.inSql("id", "SELECT paper_id FROM t_paper_author WHERE name LIKE '%" + escapeSqlLike(author.trim()) + "%'");
        }
        return wrapper;
    }

    private void applySort(QueryWrapper<Paper> wrapper, String sortBy) {
        String safeSort = sortBy == null ? "relevance" : sortBy.trim().toLowerCase(Locale.ROOT);
        switch (safeSort) {
            case "citations" -> wrapper.orderByDesc("citation_count").orderByDesc("year");
            case "latest" -> wrapper.orderByDesc("year").orderByDesc("updated_at");
            default -> wrapper.orderByDesc("year").orderByDesc("citation_count");
        }
    }

    private String sanitizeKeyword(String keyword) {
        if (keyword == null) return "";
        return keyword.replaceAll("[+\\-@*()~<>\"]", " ").replaceAll("\\s+", " ").trim();
    }

    private String escapeSqlLike(String value) {
        return value.replace("'", "''");
    }

    private PaperSearchResponse.Paper toDto(Paper entity) {
        List<PaperAuthor> authors = paperAuthorMapper.selectList(
            new QueryWrapper<PaperAuthor>()
                .eq("paper_id", entity.getId())
                .orderByAsc("sort_order"));

        PaperSearchResponse.Paper dto = new PaperSearchResponse.Paper();
        dto.setPaperId(entity.getArxivId());
        dto.setTitle(entity.getTitle());
        dto.setYear(entity.getYear());
        dto.setAbstractText(entity.getAbstractText());
        dto.setUrl(entity.getUrl());
        dto.setVenue(entity.getVenue());
        dto.setCitationCount(entity.getCitationCount() != null ? entity.getCitationCount() : 0);
        dto.setSource(entity.getSource() != null ? entity.getSource() : "local");

        if (entity.getPdfUrl() != null && !entity.getPdfUrl().isEmpty()) {
            PaperSearchResponse.OpenAccessPdf pdf = new PaperSearchResponse.OpenAccessPdf();
            pdf.setUrl(entity.getPdfUrl());
            pdf.setStatus("OA");
            dto.setOpenAccessPdf(pdf);
        }

        List<PaperSearchResponse.Author> dtoAuthors = authors.stream().map(a -> {
            PaperSearchResponse.Author author = new PaperSearchResponse.Author();
            author.setName(a.getName());
            return author;
        }).collect(Collectors.toList());
        dto.setAuthors(dtoAuthors);

        return dto;
    }

    private static class ArxivEntry {
        final Paper paper;
        final List<PaperAuthor> authors;
        ArxivEntry(Paper paper, List<PaperAuthor> authors) {
            this.paper = paper;
            this.authors = authors;
        }
    }

    private String normalizeArxivId(String idUrl) {
        if (idUrl == null) return "";
        return idUrl
            .replace("https://arxiv.org/abs/", "")
            .replace("http://arxiv.org/abs/", "")
            .strip();
    }

    private List<ArxivEntry> parseArxivResponse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        NodeList entries = doc.getElementsByTagNameNS(ATOM_NS, "entry");
        List<ArxivEntry> result = new ArrayList<>();

        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);

            String idUrl = getTextNS(entry, ATOM_NS, "id");
            String arxivId = normalizeArxivId(idUrl);

            String published = getTextNS(entry, ATOM_NS, "published");
            Integer year = null;
            if (published.length() >= 4) {
                year = Integer.parseInt(published.substring(0, 4));
            }

            String pdfUrl = null;
            NodeList linkNodes = entry.getElementsByTagNameNS(ATOM_NS, "link");
            for (int j = 0; j < linkNodes.getLength(); j++) {
                Element link = (Element) linkNodes.item(j);
                if ("pdf".equalsIgnoreCase(link.getAttribute("title"))) {
                    pdfUrl = link.getAttribute("href");
                    break;
                }
            }

            String journalRef = getTextNS(entry, ARXIV_NS, "journal_ref");

            Paper paper = new Paper();
            paper.setArxivId(arxivId);
            paper.setTitle(getTextNS(entry, ATOM_NS, "title").replaceAll("\\s+", " ").strip());
            paper.setAbstractText(getTextNS(entry, ATOM_NS, "summary").replaceAll("\\s+", " ").strip());
            paper.setYear(year);
            paper.setUrl(idUrl);
            paper.setPdfUrl(pdfUrl);
            paper.setVenue(journalRef.isEmpty() ? null : journalRef);
            paper.setCitationCount(0);
            paper.setSource("arxiv");

            List<PaperAuthor> authors = new ArrayList<>();
            NodeList authorNodes = entry.getElementsByTagNameNS(ATOM_NS, "author");
            for (int j = 0; j < authorNodes.getLength(); j++) {
                Element a = (Element) authorNodes.item(j);
                PaperAuthor author = new PaperAuthor();
                author.setName(getTextNS(a, ATOM_NS, "name"));
                author.setSortOrder(j);
                authors.add(author);
            }

            result.add(new ArxivEntry(paper, authors));
        }

        return result;
    }

    private String getTextNS(Element parent, String ns, String localName) {
        NodeList list = parent.getElementsByTagNameNS(ns, localName);
        if (list.getLength() == 0) return "";
        Node node = list.item(0);
        return node.getTextContent() != null ? node.getTextContent() : "";
    }
}
