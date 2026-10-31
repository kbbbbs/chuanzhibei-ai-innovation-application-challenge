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

import java.util.Comparator;
import java.util.concurrent.TimeUnit;

@Service
public class PaperSearchServiceImpl implements PaperSearchService {

    private static final String API_BASE = "https://api.semanticscholar.org/graph/v1";
    private static final String SEARCH_FIELDS =
        "title,authors,year,abstract,url,openAccessPdf,venue,citationCount,publicationDate,externalIds";

    @Value("${academic.semantic-scholar.api-key:}")
    private String apiKey;

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override
    public Result searchPapers(String keyword, Integer page, Integer size, String sortBy) {
        try {
            int offset = (page - 1) * size;
            String fields = SEARCH_FIELDS;
            String url = API_BASE + "/paper/search"
                + "?query=" + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&limit=" + size
                + "&offset=" + offset
                + "&fields=" + fields;

            Request.Builder reqBuilder = new Request.Builder().url(url).get();
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("x-api-key", apiKey);
            }
            Response response = client.newCall(reqBuilder.build()).execute();

            if (response.code() == 429) {
                return Result.error("429", "Semantic Scholar 请求过于频繁，请稍后重试或使用 arXiv / OpenAlex 数据源");
            }
            if (!response.isSuccessful()) {
                return Result.error("Semantic Scholar API returned " + response.code());
            }

            String body = response.body().string();
            PaperSearchResponse result = JSON.parseObject(body, PaperSearchResponse.class);
            if (result.getData() != null) {
                result.getData().forEach(p -> p.setSource("semantic_scholar"));
                applySort(result, sortBy);
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

            if (response.code() == 429) {
                return Result.error("429", "Semantic Scholar 请求过于频繁，请稍后重试或使用 arXiv / OpenAlex 数据源");
            }
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

    private void applySort(PaperSearchResponse result, String sortBy) {
        if (result == null || result.getData() == null || sortBy == null) {
            return;
        }
        if ("latest".equalsIgnoreCase(sortBy)) {
            result.getData().sort(Comparator
                .comparing(PaperSearchResponse.Paper::getYear, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PaperSearchResponse.Paper::getCitationCount, Comparator.nullsLast(Comparator.reverseOrder())));
        } else if ("citations".equalsIgnoreCase(sortBy)) {
            result.getData().sort(Comparator
                .comparing(PaperSearchResponse.Paper::getCitationCount, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PaperSearchResponse.Paper::getYear, Comparator.nullsLast(Comparator.reverseOrder())));
        }
    }
}
