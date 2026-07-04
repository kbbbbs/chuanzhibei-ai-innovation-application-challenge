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

    private static final String API_BASE = "http://export.arxiv.org/api/query";
    private static final int ARXIV_MAX = 100;
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final String OPENSEARCH_NS = "http://a9.com/-/spec/opensearch/1.1/";
    private static final String ARXIV_NS = "http://arxiv.org/schemas/atom";

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public Result search(String keyword, Integer page, Integer size) {
        try {
            int start = (page - 1) * size;
            int maxResults = Math.min(size, ARXIV_MAX - start);
            if (maxResults <= 0) return Result.success(emptyResponse());

            String url = API_BASE
                + "?search_query=all:" + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&start=" + start
                + "&max_results=" + maxResults
                + "&sortBy=relevance";

            Request request = new Request.Builder().url(url).get().build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                return Result.error("arXiv API returned " + response.code());
            }

            String body = response.body().string();
            return Result.success(parseArxivXml(body));
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
            String paperId = idUrl.replace("http://arxiv.org/abs/", "").strip();
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
