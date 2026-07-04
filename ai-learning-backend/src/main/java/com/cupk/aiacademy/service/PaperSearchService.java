package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.PaperSearchResponse;

public interface PaperSearchService {
    Result searchPapers(String keyword, Integer page, Integer size);
    Result getPaperDetail(String paperId);
}
