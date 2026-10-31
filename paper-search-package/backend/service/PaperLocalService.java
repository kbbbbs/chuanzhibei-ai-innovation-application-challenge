package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.PaperSearchResponse;

public interface PaperLocalService {
    Result<PaperSearchResponse> search(String keyword, Integer page, Integer size);
    Result<PaperSearchResponse.Paper> getDetail(String paperId);
    Result<Integer> importFromArxiv(String keyword, Integer maxResults);
}
