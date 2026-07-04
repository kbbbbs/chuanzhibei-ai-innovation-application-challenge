package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.PaperSearchResponse;
import com.cupk.aiacademy.entity.Paper;
import com.cupk.aiacademy.entity.PaperAuthor;
import com.cupk.aiacademy.mapper.PaperAuthorMapper;
import com.cupk.aiacademy.mapper.PaperMapper;
import com.cupk.aiacademy.pojo.Course;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaperRecommendService {

    private final PaperMapper paperMapper;
    private final PaperAuthorMapper paperAuthorMapper;

    public PaperRecommendService(PaperMapper paperMapper, PaperAuthorMapper paperAuthorMapper) {
        this.paperMapper = paperMapper;
        this.paperAuthorMapper = paperAuthorMapper;
    }

    /**
     * 根据课程推荐论文
     * @param courses 学生选修或教师教授的课程列表
     * @param maxPerCourse 每门课程最多推荐几篇
     * @param maxTotal 总共最多推荐几篇
     */
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

        PaperSearchResponse response = new PaperSearchResponse();
        response.setTotal(results.size());
        response.setData(results);
        return Result.success(response);
    }

    /**
     * 从课程标题和描述中提取搜索关键词
     */
    private List<String> extractKeywords(Course course) {
        List<String> terms = new ArrayList<>();
        String title = (course.getTitle() != null ? course.getTitle() : "") + " " +
                (course.getDescription() != null ? course.getDescription() : "");
        String lower = title.toLowerCase();

        // 课程名称 → 学术关键词映射
        if (lower.contains("java")) { terms.add("Java"); terms.add("Spring"); terms.add("JVM"); }
        if (lower.contains("python")) { terms.add("Python"); terms.add("machine learning"); }
        if (lower.contains("vue") || lower.contains("前端")) { terms.add("frontend"); terms.add("Vue"); terms.add("component"); }
        if (lower.contains("mysql") || title.contains("数据库")) { terms.add("MySQL"); terms.add("database"); terms.add("SQL"); }
        if (title.contains("算法") || title.contains("数据结构")) {
            terms.add("algorithm"); terms.add("sorting"); terms.add("graph"); terms.add("data structure"); }
        if (title.contains("英语") || title.contains("写作") || title.contains("学术")) {
            terms.add("academic writing"); terms.add("English"); terms.add("citation"); }
        if (title.contains("操作系统") || lower.contains("os")) {
            terms.add("operating system"); terms.add("Linux"); terms.add("kernel"); }
        if (title.contains("网络")) { terms.add("network"); terms.add("TCP"); terms.add("protocol"); }
        if (title.contains("人工智能") || lower.contains("ai")) { terms.add("machine learning"); terms.add("deep learning"); terms.add("neural network"); }
        if (title.contains("机器学习")) { terms.add("machine learning"); terms.add("supervised learning"); terms.add("classification"); }
        if (title.contains("深度学习")) { terms.add("deep learning"); terms.add("CNN"); terms.add("transformer"); }
        if (title.contains("嵌入") || title.contains("嵌入式")) { terms.add("embedded system"); terms.add("IoT"); terms.add("microcontroller"); }
        if (title.contains("编译")) { terms.add("compiler"); terms.add("parser"); terms.add("LLVM"); }

        // 如果没匹配到，用课程标题自身作为检索词
        if (terms.isEmpty() && course.getTitle() != null) {
            for (String word : course.getTitle().split("[\\s，,、]+")) {
                if (word.length() >= 2) terms.add(word);
            }
        }

        return terms;
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
        List<PaperAuthor> authors = paperAuthorMapper.selectList(
                new QueryWrapper<PaperAuthor>().eq("paper_id", entity.getId()).orderByAsc("sort_order"));

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

        dto.setAuthors(authors.stream().map(a -> {
            PaperSearchResponse.Author author = new PaperSearchResponse.Author();
            author.setName(a.getName());
            return author;
        }).collect(Collectors.toList()));

        return dto;
    }

    private PaperSearchResponse emptyResponse() {
        PaperSearchResponse r = new PaperSearchResponse();
        r.setTotal(0);
        r.setData(Collections.emptyList());
        return r;
    }
}
