package com.cupk.aiacademy.service.impl;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.PaperSearchResponse;
import com.cupk.aiacademy.service.ArxivSearchService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ArxivSearchServiceImpl implements ArxivSearchService {

    private static final String API_BASE = "https://export.arxiv.org/api/query";
    private static final String USER_AGENT = "AI-Academy/1.0 (mailto:2024015839@cupk.edu.cn)";
    private static final int ARXIV_MAX = 100;
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final String OPENSEARCH_NS = "http://a9.com/-/spec/opensearch/1.1/";
    private static final String ARXIV_NS = "http://arxiv.org/schemas/atom";

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build();

    @Override
    public Result search(String keyword, Integer page, Integer size, String sortBy) {
        try {
            String q = keyword == null ? "" : keyword.trim();
            if (q.isBlank()) {
                return Result.success(emptyResponse());
            }

            int safePage = page == null || page < 1 ? 1 : page;
            int safeSize = size == null || size < 1 ? 10 : Math.min(size, ARXIV_MAX);
            int start = (safePage - 1) * safeSize;

            String arxivSortBy = "relevance";
            if ("latest".equalsIgnoreCase(sortBy)) {
                arxivSortBy = "submittedDate";
            }

            String url = API_BASE
                + "?search_query=all:" + java.net.URLEncoder.encode(q, "UTF-8")
                + "&start=" + start
                + "&max_results=" + safeSize
                + "&sortBy=" + arxivSortBy;

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 429) {
                    return Result.error("429", "arXiv 请求过于频繁，请稍后重试或切换到 OpenAlex / Semantic Scholar");
                }
                if (!response.isSuccessful()) {
                    return Result.error("arXiv API returned " + response.code());
                }
                if (response.body() == null) {
                    return Result.error("arXiv API returned empty body");
                }

                String body = response.body().string();
                return Result.success(parseArxivXml(body));
            }
        } catch (Exception e) {
            return Result.error("Failed to search arXiv: " + e.getMessage());
        }
    }

    private PaperSearchResponse parseArxivXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        NodeList entries = doc.getElementsByTagNameNS(ATOM_NS, "entry");
        List<PaperSearchResponse.Paper> papers = new ArrayList<>();

        NodeList totalNodes = doc.getElementsByTagNameNS(OPENSEARCH_NS, "totalResults");
        int total = totalNodes.getLength() > 0
            ? Integer.parseInt(totalNodes.item(0).getTextContent())
            : entries.getLength();

        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            PaperSearchResponse.Paper paper = new PaperSearchResponse.Paper();

            String idUrl = getTextNS(entry, ATOM_NS, "id");
            String paperId = normalizeArxivId(idUrl);
            paper.setPaperId(paperId);

            paper.setTitle(getTextNS(entry, ATOM_NS, "title").replaceAll("\\s+", " ").strip());
            paper.setUrl(idUrl);
            paper.setAbstractText(getTextNS(entry, ATOM_NS, "summary").replaceAll("\\s+", " ").strip());

            String published = getTextNS(entry, ATOM_NS, "published");
            if (published.length() >= 4) {
                paper.setYear(Integer.parseInt(published.substring(0, 4)));
            }

            NodeList authorNodes = entry.getElementsByTagNameNS(ATOM_NS, "author");
            List<PaperSearchResponse.Author> authors = new ArrayList<>();
            for (int j = 0; j < authorNodes.getLength(); j++) {
                Element a = (Element) authorNodes.item(j);
                PaperSearchResponse.Author author = new PaperSearchResponse.Author();
                author.setName(getTextNS(a, ATOM_NS, "name"));
                authors.add(author);
            }
            paper.setAuthors(authors);

            NodeList linkNodes = entry.getElementsByTagNameNS(ATOM_NS, "link");
            for (int j = 0; j < linkNodes.getLength(); j++) {
                Element link = (Element) linkNodes.item(j);
                String title = link.getAttribute("title");
                if ("pdf".equalsIgnoreCase(title)) {
                    PaperSearchResponse.OpenAccessPdf pdf = new PaperSearchResponse.OpenAccessPdf();
                    pdf.setUrl(link.getAttribute("href"));
                    paper.setOpenAccessPdf(pdf);
                    break;
                }
            }

            String journalRef = getTextNS(entry, ARXIV_NS, "journal_ref");
            paper.setVenue(journalRef.isEmpty() ? null : journalRef);

            paper.setSource("arxiv");
            papers.add(paper);
        }

        PaperSearchResponse result = new PaperSearchResponse();
        result.setTotal(total);
        result.setData(papers);
        return result;
    }

    private String normalizeArxivId(String idUrl) {
        if (idUrl == null) return "";
        return idUrl
            .replace("https://arxiv.org/abs/", "")
            .replace("http://arxiv.org/abs/", "")
            .strip();
    }

    private String getTextNS(Element parent, String ns, String localName) {
        NodeList list = parent.getElementsByTagNameNS(ns, localName);
        if (list.getLength() == 0) return "";
        Node node = list.item(0);
        return node.getTextContent() != null ? node.getTextContent() : "";
    }

    private PaperSearchResponse emptyResponse() {
        PaperSearchResponse r = new PaperSearchResponse();
        r.setTotal(0);
        r.setData(new ArrayList<>());
        return r;
    }
}
