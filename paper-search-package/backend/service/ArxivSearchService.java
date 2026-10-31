package com.cupk.aiacademy.service;

import com.cupk.aiacademy.dto.PaperSearchResponse;
import com.cupk.aiacademy.common.Result;

public interface ArxivSearchService {
    Result<PaperSearchResponse> search(String keyword, Integer page, Integer size);
}
