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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PaperLocalServiceImpl implements PaperLocalService {

    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final String ARXIV_NS = "http://arxiv.org/schemas/atom";
    private static final String ARXIV_API = "http://export.arxiv.org/api/query";

    @Autowired
    private PaperMapper paperMapper;

    @Autowired
    private PaperAuthorMapper paperAuthorMapper;

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public Result search(String keyword, Integer page, Integer size) {
        try {
            String safe = sanitizeKeyword(keyword);
            QueryWrapper<Paper> wrapper = new QueryWrapper<>();
            wrapper.apply("MATCH(title, abstract_text) AGAINST({0} IN BOOLEAN MODE)", safe);
            wrapper.orderByDesc("year");

            Page<Paper> pageParam = new Page<>(page, size);
            Page<Paper> pageResult = paperMapper.selectPage(pageParam, wrapper);

            if (pageResult.getTotal() == 0) {
                wrapper = new QueryWrapper<>();
                wrapper.like("title", keyword).or().like("abstract_text", keyword);
                wrapper.orderByDesc("year");
                pageResult = paperMapper.selectPage(pageParam, wrapper);
            }

            List<PaperSearchResponse.Paper> papers = pageResult.getRecords().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

            PaperSearchResponse response = new PaperSearchResponse();
            response.setTotal((int) pageResult.getTotal());
            response.setData(papers);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error("本地论文搜索失败: " + e.getMessage());
        }
    }

    @Override
    public Result getDetail(String paperId) {
        try {
            Paper paper = paperMapper.selectOne(
                new QueryWrapper<Paper>().eq("arxiv_id", paperId));
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
            int capped = Math.min(maxResults, 100);
            String url = ARXIV_API
                + "?search_query=all:" + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&start=0&max_results=" + capped
                + "&sortBy=relevance";

            Request request = new Request.Builder().url(url).get().build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                return Result.error("arXiv API returned " + response.code());
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
        } catch (Exception e) {
            return Result.error("导入论文失败: " + e.getMessage());
        }
    }

    private String sanitizeKeyword(String keyword) {
        return keyword.replaceAll("[+\\-@*()~<>\"]", " ").replaceAll("\\s+", " ").trim();
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
        dto.setSource("local");

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
            String arxivId = idUrl.replace("http://arxiv.org/abs/", "").strip();

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
