package com.cupk.aiacademy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.PaperSearchResponse;
import com.cupk.aiacademy.service.PaperSearchService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class PaperSearchServiceImpl implements PaperSearchService {

    private static final String API_BASE = "https://api.semanticscholar.org/graph/v1";
    private static final String SEARCH_FIELDS =
        "title,authors,year,abstract,url,openAccessPdf,venue,citationCount";

    @Value("${academic.semantic-scholar.api-key:}")
    private String apiKey;

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public Result searchPapers(String keyword, Integer page, Integer size) {
        try {
            int offset = (page - 1) * size;
            String url = API_BASE + "/paper/search"
                + "?query=" + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&limit=" + size
                + "&offset=" + offset
                + "&fields=" + SEARCH_FIELDS;

            Request.Builder reqBuilder = new Request.Builder().url(url).get();
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("x-api-key", apiKey);
            }
            Response response = client.newCall(reqBuilder.build()).execute();

            if (!response.isSuccessful()) {
                return Result.error("Semantic Scholar API returned " + response.code());
            }

            String body = response.body().string();
            PaperSearchResponse result = JSON.parseObject(body, PaperSearchResponse.class);
            if (result.getData() != null) {
                result.getData().forEach(p -> p.setSource("semantic_scholar"));
            }
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("Failed to search papers: " + e.getMessage());
        }
    }

    @Override
    public Result getPaperDetail(String paperId) {
        try {
            String url = API_BASE + "/paper/" + paperId
                + "?fields=" + SEARCH_FIELDS + ",tldr,referenceCount";

            Request.Builder reqBuilder = new Request.Builder().url(url).get();
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("x-api-key", apiKey);
            }
            Response response = client.newCall(reqBuilder.build()).execute();

            if (!response.isSuccessful()) {
                return Result.error("Semantic Scholar API returned " + response.code());
            }

            String body = response.body().string();
            PaperSearchResponse.Paper paper = JSON.parseObject(body, PaperSearchResponse.Paper.class);
            paper.setSource("semantic_scholar");
            return Result.success(paper);
        } catch (Exception e) {
            return Result.error("Failed to get paper detail: " + e.getMessage());
        }
    }
}
