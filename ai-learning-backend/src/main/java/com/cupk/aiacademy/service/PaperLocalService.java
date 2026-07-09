package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.Result;

public interface PaperLocalService {
    Result search(String keyword, Integer page, Integer size,
                  String author, String venue,
                  Integer yearFrom, Integer yearTo,
                  Integer minCitations, String sortBy,
                  Boolean openAccessOnly);
    Result getDetail(String paperId);
    Result importFromArxiv(String keyword, Integer maxResults);
    Result getCourseDirections(String keyword);
}
