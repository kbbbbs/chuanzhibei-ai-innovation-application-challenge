package com.cupk.aiacademy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cupk.aiacademy.dto.PaperSearchResponse;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.service.OpenAlexSearchService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OpenAlexSearchServiceImpl implements OpenAlexSearchService {

    private static final String API_BASE = "https://api.openalex.org/works";

    @Value("${academic.openalex.email:student@cupk.edu.cn}")
    private String contactEmail;

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public Result<PaperSearchResponse> search(String keyword, Integer page, Integer size) {
        try {
            String url = API_BASE
                + "?search=" + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&per_page=" + size
                + "&page=" + page;

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "mailto:" + contactEmail)
                .get()
                .build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                return Result.error("OpenAlex API returned " + response.code());
            }

            String body = response.body().string();
            JSONObject json = JSON.parseObject(body);
            JSONArray results = json.getJSONArray("results");
            JSONObject meta = json.getJSONObject("meta");

            List<PaperSearchResponse.Paper> papers = new ArrayList<>();
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    PaperSearchResponse.Paper paper = new PaperSearchResponse.Paper();

                    // ID: strip "https://openalex.org/" prefix
                    String openAlexId = item.getString("id");
                    paper.setPaperId(openAlexId != null
                        ? openAlexId.replace("https://openalex.org/", "")
                        : "");
                    paper.setTitle(item.getString("title"));

                    // Abstract: OpenAlex uses inverted index, extract if available
                    String abstractText = item.getString("abstract");
                    // Fallback: try abstract_inverted_index reconstruction
                    if (abstractText == null) {
                        JSONObject invIdx = item.getJSONObject("abstract_inverted_index");
                        if (invIdx != null) {
                            abstractText = buildAbstractFromIndex(invIdx);
                        }
                    }
                    paper.setAbstractText(abstractText);

                    // Year from publication_date
                    String pubDate = item.getString("publication_date");
                    if (pubDate != null && pubDate.length() >= 4) {
                        paper.setYear(Integer.parseInt(pubDate.substring(0, 4)));
                    }

                    // Authors
                    JSONArray authorships = item.getJSONArray("authorships");
                    List<PaperSearchResponse.Author> authors = new ArrayList<>();
                    if (authorships != null) {
                        for (int j = 0; j < authorships.size(); j++) {
                            JSONObject as = authorships.getJSONObject(j);
                            JSONObject authorObj = as.getJSONObject("author");
                            if (authorObj != null) {
                                PaperSearchResponse.Author author = new PaperSearchResponse.Author();
                                author.setName(authorObj.getString("display_name"));
                                authors.add(author);
                            }
                        }
                    }
                    paper.setAuthors(authors);

                    // URL: prefer DOI, fallback to primary_location landing page
                    String doi = item.getString("doi");
                    if (doi != null) {
                        paper.setUrl("https://doi.org/" + doi);
                    } else {
                        JSONObject primaryLoc = item.getJSONObject("primary_location");
                        if (primaryLoc != null) {
                            paper.setUrl(primaryLoc.getString("landing_page_url"));
                        }
                    }

                    // PDF from open_access
                    JSONObject oa = item.getJSONObject("open_access");
                    if (oa != null && oa.getBooleanValue("is_oa", false)) {
                        PaperSearchResponse.OpenAccessPdf pdf = new PaperSearchResponse.OpenAccessPdf();
                        pdf.setUrl(oa.getString("oa_url"));
                        pdf.setStatus(oa.getString("oa_status"));
                        paper.setOpenAccessPdf(pdf);
                    }

                    paper.setCitationCount(item.getIntValue("cited_by_count", 0));

                    // Venue from primary_location
                    JSONObject primaryLoc = item.getJSONObject("primary_location");
                    if (primaryLoc != null) {
                        JSONObject source = primaryLoc.getJSONObject("source");
                        if (source != null) {
                            paper.setVenue(source.getString("display_name"));
                        }
                    }

                    paper.setSource("openalex");
                    papers.add(paper);
                }
            }

            PaperSearchResponse result = new PaperSearchResponse();
            result.setTotal(meta != null ? meta.getIntValue("count", 0) : 0);
            result.setData(papers);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("Failed to search OpenAlex: " + e.getMessage());
        }
    }

    private String buildAbstractFromIndex(JSONObject invIdx) {
        if (invIdx == null) return null;
        // The inverted index maps word -> [positions], reconstruct by position order
        int maxPos = 0;
        for (String key : invIdx.keySet()) {
            JSONArray positions = invIdx.getJSONArray(key);
            if (positions != null) {
                for (int i = 0; i < positions.size(); i++) {
                    int pos = positions.getIntValue(i);
                    if (pos > maxPos) maxPos = pos;
                }
            }
        }
        String[] words = new String[maxPos + 1];
        for (String key : invIdx.keySet()) {
            JSONArray positions = invIdx.getJSONArray(key);
            if (positions != null) {
                for (int i = 0; i < positions.size(); i++) {
                    int pos = positions.getIntValue(i);
                    if (pos < words.length) {
                        words[pos] = key;
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w != null && sb.length() > 0) sb.append(' ');
            if (w != null) sb.append(w);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
