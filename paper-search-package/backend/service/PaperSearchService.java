package com.cupk.aiacademy.service;

import com.cupk.aiacademy.dto.PaperSearchResponse;
import com.cupk.aiacademy.common.Result;

public interface PaperSearchService {
    Result<PaperSearchResponse> searchPapers(String keyword, Integer page, Integer size);
    Result<PaperSearchResponse.Paper> getPaperDetail(String paperId);
}
